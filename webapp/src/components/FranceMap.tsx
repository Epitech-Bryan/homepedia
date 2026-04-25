import { memo, useCallback, useEffect, useMemo, useState } from "react";
import {
  MapContainer,
  TileLayer,
  GeoJSON as LeafletGeoJSON,
  CircleMarker,
  Tooltip,
  useMap,
} from "react-leaflet";
import L from "leaflet";
import "leaflet.heat";
import type { Layer, LeafletMouseEvent, PathOptions } from "leaflet";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import "leaflet/dist/leaflet.css";

const FRANCE_CENTER: [number, number] = [46.6, 2.5];
const FRANCE_ZOOM = 6;

// Sequential 7-step Carto-style "Sunset" palette — works for prices, density,
// any positive metric. Perceptually monotone, accessible.
const CHOROPLETH_SCALE = [
  "#fef0d9",
  "#fdd49e",
  "#fdbb84",
  "#fc8d59",
  "#ef6548",
  "#d7301f",
  "#990000",
];

// Hue used for default polygons + bubbles (warm orange to match the palette).
const ACCENT = "#fc8d59";
const ACCENT_DARK = "#b3502c";
const ACTIVE_RING = "#1f2937";

interface FeatureProperties {
  code?: string;
  name?: string;
  nom?: string;
}

export interface MapMarker {
  id: string;
  lat: number;
  lon: number;
  name: string;
}

export type MapStyle = "choropleth" | "bubbles" | "heat" | "all";

interface FranceMapProps {
  geojson: GeoJSON.FeatureCollection | null;
  onFeatureClick?: (code: string, name: string) => void;
  markers?: MapMarker[];
  onMarkerClick?: (id: string) => void;
  activeFeatureCode?: string;
  metricByCode?: Record<string, number | null | undefined>;
  metricLabel?: string;
  mapStyle?: MapStyle;
  height?: string;
}

// Cities only become visible past this zoom level so they don't hide the
// choropleth at department/region scale.
const CITY_MARKER_MIN_ZOOM = 8;

function ZoomAwareCityMarkers({
  markers,
  onMarkerClick,
}: {
  markers: MapMarker[];
  onMarkerClick?: (id: string) => void;
}) {
  const map = useMap();
  const [zoom, setZoom] = useState(() => map.getZoom());

  useEffect(() => {
    const update = () => setZoom(map.getZoom());
    map.on("zoomend", update);
    return () => {
      map.off("zoomend", update);
    };
  }, [map]);

  if (zoom < CITY_MARKER_MIN_ZOOM) return null;

  // Smooth fade-in: bigger and more opaque as the user zooms in further.
  const t = Math.min(1, (zoom - CITY_MARKER_MIN_ZOOM) / 3);
  const radius = 2.5 + t * 2.5;
  const opacity = 0.4 + t * 0.5;

  return (
    <>
      {markers.map((m) => (
        <CircleMarker
          key={m.id}
          center={[m.lat, m.lon]}
          radius={radius}
          pathOptions={{
            fillColor: "#1f2937",
            color: "#1f2937",
            fillOpacity: opacity,
            weight: 0,
          }}
          eventHandlers={{
            click: () => onMarkerClick?.(m.id),
          }}
        >
          <Tooltip sticky direction="top" offset={[0, -4]}>
            {m.name}
          </Tooltip>
        </CircleMarker>
      ))}
    </>
  );
}

function HeatLayer({ points }: { points: L.HeatLatLngTuple[] }) {
  const map = useMap();
  useEffect(() => {
    if (!points || points.length === 0) return;
    const layer = L.heatLayer(points, {
      radius: 24,
      blur: 20,
      maxZoom: 12,
      minOpacity: 0.3,
      gradient: { 0.2: "#fef0d9", 0.5: "#fdbb84", 0.7: "#ef6548", 0.9: "#990000" },
    });
    layer.addTo(map);
    return () => {
      layer.remove();
    };
  }, [map, points]);
  return null;
}

function FitBounds({
  geojson,
  activeFeatureCode,
}: {
  geojson: GeoJSON.FeatureCollection | null;
  activeFeatureCode?: string;
}) {
  const map = useMap();
  useEffect(() => {
    if (!geojson || !geojson.features?.length) {
      map.setView(FRANCE_CENTER, FRANCE_ZOOM);
      return;
    }
    let features = geojson.features;
    if (activeFeatureCode) {
      const filtered = features.filter(
        (f) => (f.properties as FeatureProperties | null)?.code === activeFeatureCode,
      );
      if (filtered.length > 0) features = filtered;
    }
    const fc: GeoJSON.FeatureCollection = { type: "FeatureCollection", features };
    const layer = L.geoJSON(fc);
    const bounds = layer.getBounds();
    if (bounds.isValid()) {
      map.flyToBounds(bounds, { padding: [32, 32], duration: 0.7 });
    }
  }, [geojson, activeFeatureCode, map]);
  return null;
}

function formatValue(value: number): string {
  if (value >= 1_000_000) return (value / 1_000_000).toFixed(1) + "M";
  if (value >= 1_000) return (value / 1_000).toFixed(1) + "k";
  return Math.round(value).toLocaleString("fr-FR");
}

function Legend({ range, label }: { range: { min: number; max: number }; label?: string }) {
  const gradient = `linear-gradient(90deg, ${CHOROPLETH_SCALE.join(", ")})`;
  return (
    <div className="absolute bottom-3 left-3 z-[400] rounded-md border border-border/60 bg-background/90 px-3 py-2 text-[11px] text-foreground shadow-md backdrop-blur">
      {label && <div className="mb-1 font-medium uppercase tracking-wide">{label}</div>}
      <div className="h-1.5 w-44 rounded-full" style={{ background: gradient }} />
      <div className="mt-1 flex justify-between text-muted-foreground">
        <span>{formatValue(range.min)}</span>
        <span>{formatValue(range.max)}</span>
      </div>
    </div>
  );
}

function FranceMapComponent({
  geojson,
  onFeatureClick,
  markers,
  onMarkerClick,
  activeFeatureCode,
  metricByCode,
  metricLabel,
  mapStyle = "choropleth",
  height = "500px",
}: FranceMapProps) {
  const showChoropleth = mapStyle === "choropleth" || mapStyle === "all";
  const showBubbles = mapStyle === "bubbles" || mapStyle === "all";
  const showHeat = mapStyle === "heat" || mapStyle === "all";

  const choroplethRange = useMemo(() => {
    if (!metricByCode) return null;
    const values = Object.values(metricByCode).filter(
      (v): v is number => typeof v === "number" && Number.isFinite(v) && v > 0,
    );
    if (values.length === 0) return null;
    const min = Math.min(...values);
    const max = Math.max(...values);
    if (min === max) return null;
    return { min, max };
  }, [metricByCode]);

  const colorForCode = useCallback(
    (code: string | undefined): string | null => {
      if (!code || !metricByCode || !choroplethRange || !showChoropleth) return null;
      const value = metricByCode[code];
      if (value == null || !Number.isFinite(value) || value <= 0) return null;
      const ratio = (value - choroplethRange.min) / (choroplethRange.max - choroplethRange.min);
      const idx = Math.min(
        CHOROPLETH_SCALE.length - 1,
        Math.floor(ratio * CHOROPLETH_SCALE.length),
      );
      return CHOROPLETH_SCALE[idx];
    },
    [metricByCode, choroplethRange, showChoropleth],
  );

  const polygonCenters = useMemo(() => {
    if (!geojson || !metricByCode || !choroplethRange) return [];
    const result: Array<{
      code: string;
      name: string;
      lat: number;
      lng: number;
      value: number;
      ratio: number;
    }> = [];
    for (const feature of geojson.features) {
      const props = feature.properties as FeatureProperties | null;
      const code = props?.code;
      if (!code) continue;
      const value = metricByCode[code];
      if (value == null || !Number.isFinite(value) || value <= 0) continue;
      const layer = L.geoJSON(feature);
      const center = layer.getBounds().getCenter();
      const ratio = (value - choroplethRange.min) / (choroplethRange.max - choroplethRange.min);
      result.push({
        code,
        name: props?.name ?? props?.nom ?? "",
        lat: center.lat,
        lng: center.lng,
        value,
        ratio,
      });
    }
    return result;
  }, [geojson, metricByCode, choroplethRange]);

  const bubbles = useMemo(() => {
    if (!showBubbles) return [];
    return polygonCenters.map((p) => ({ ...p, radius: 6 + p.ratio * 22 }));
  }, [polygonCenters, showBubbles]);

  const heatPoints = useMemo<L.HeatLatLngTuple[]>(() => {
    if (!showHeat) return [];
    if (markers && markers.length > 0) {
      return markers.map((m) => [m.lat, m.lon, 1] as L.HeatLatLngTuple);
    }
    return polygonCenters.map((p) => [p.lat, p.lng, p.ratio] as L.HeatLatLngTuple);
  }, [showHeat, markers, polygonCenters]);

  const baseStyle = useCallback(
    (feature?: GeoJSON.Feature<GeoJSON.Geometry, FeatureProperties>): PathOptions => {
      const code = feature?.properties?.code;
      const choroplethColor = colorForCode(code);
      const isActive = !!activeFeatureCode && code === activeFeatureCode;

      // Default style: virtually no fill, just thin stroke. Lets the basemap
      // breathe and keeps the UI uncluttered when no metric is selected.
      if (!choroplethColor) {
        return {
          fillColor: ACCENT,
          fillOpacity: isActive ? 0.18 : 0.06,
          color: isActive ? ACTIVE_RING : ACCENT_DARK,
          weight: isActive ? 2.5 : 1,
          opacity: isActive ? 1 : 0.55,
          dashArray: isActive ? undefined : "1 0",
        };
      }

      // Choropleth style: full fill + dark border for active feature.
      return {
        fillColor: choroplethColor,
        fillOpacity: isActive ? 0.9 : 0.78,
        color: isActive ? ACTIVE_RING : "#7c2d12",
        weight: isActive ? 2.5 : 0.8,
        opacity: 1,
      };
    },
    [colorForCode, activeFeatureCode],
  );

  const onEachFeature = useCallback(
    (feature: GeoJSON.Feature<GeoJSON.Geometry, FeatureProperties>, layer: Layer) => {
      const props = feature.properties;
      const name = props?.name ?? props?.nom ?? "";
      const code = props?.code ?? "";
      const value = metricByCode?.[code];
      const hasChoropleth = colorForCode(code) !== null;

      const tooltipText =
        value != null && Number.isFinite(value)
          ? `<strong>${name}</strong><br/>${formatValue(value)}${metricLabel ? ` · ${metricLabel}` : ""}`
          : `<strong>${name}</strong>`;
      layer.bindTooltip(tooltipText, { sticky: true, direction: "top", offset: [0, -8] });

      const resetStyle = () => (layer as L.Path).setStyle(baseStyle(feature));

      layer.on({
        mouseover: (e: LeafletMouseEvent) => {
          const target = e.target as L.Path;
          if (hasChoropleth) {
            // Choropleth mode: bump fill opacity + darken stroke.
            target.setStyle({ fillOpacity: 0.95, weight: 2.5, color: ACTIVE_RING });
          } else {
            // Default mode: just thicken the stroke, keep fill almost
            // transparent so hover doesn't flood the polygon with orange.
            target.setStyle({ fillOpacity: 0.14, weight: 2, color: ACTIVE_RING, opacity: 1 });
          }
          target.bringToFront();
        },
        mouseout: resetStyle,
        // Leaflet sometimes drops mouseout when the cursor stays still during
        // a zoom; resetting on zoomstart guarantees no stale hover state.
        zoomstart: resetStyle,
        click: () => {
          if (onFeatureClick && code) {
            onFeatureClick(code, name);
          }
        },
      });

      (layer as L.Path).setStyle(baseStyle(feature));
    },
    [onFeatureClick, metricByCode, metricLabel, baseStyle, colorForCode],
  );

  const layerKey = useMemo(() => {
    const sample = geojson ? JSON.stringify(geojson).slice(0, 80) : "";
    const metricKey = metricByCode
      ? Object.keys(metricByCode).length + ":" + JSON.stringify(choroplethRange)
      : "no-metric";
    return `${sample}|${metricKey}|${activeFeatureCode ?? ""}`;
  }, [geojson, metricByCode, choroplethRange, activeFeatureCode]);

  return (
    <Card className="overflow-hidden border-border/60 shadow-sm">
      <CardContent className="p-0">
        <div className="relative" style={{ height }}>
          {!geojson ? (
            <Skeleton className="h-full w-full" />
          ) : (
            <>
              <MapContainer
                center={FRANCE_CENTER}
                zoom={FRANCE_ZOOM}
                scrollWheelZoom={true}
                zoomControl={false}
                style={{ width: "100%", height: "100%", background: "#f4f1ec" }}
              >
                <TileLayer
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
                  url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager_nolabels/{z}/{x}/{y}{r}.png"
                  subdomains="abcd"
                  maxZoom={19}
                />
                <LeafletGeoJSON
                  key={layerKey}
                  data={geojson}
                  style={baseStyle}
                  onEachFeature={onEachFeature}
                />
                {bubbles.map((b) => (
                  <CircleMarker
                    key={`bubble-${b.code}`}
                    center={[b.lat, b.lng]}
                    radius={b.radius}
                    pathOptions={{
                      fillColor: ACCENT,
                      color: ACCENT_DARK,
                      fillOpacity: 0.6,
                      weight: 1.5,
                    }}
                    eventHandlers={{
                      click: () => onFeatureClick?.(b.code, b.name),
                    }}
                  >
                    <Tooltip sticky direction="top" offset={[0, -8]}>
                      <strong>{b.name}</strong>
                      <br />
                      {formatValue(b.value)}
                      {metricLabel ? ` · ${metricLabel}` : ""}
                    </Tooltip>
                  </CircleMarker>
                ))}
                {markers && markers.length > 0 && (
                  <ZoomAwareCityMarkers markers={markers} onMarkerClick={onMarkerClick} />
                )}
                {showHeat && <HeatLayer points={heatPoints} />}
                <FitBounds geojson={geojson} activeFeatureCode={activeFeatureCode} />
              </MapContainer>
              {showChoropleth && choroplethRange && (
                <Legend range={choroplethRange} label={metricLabel} />
              )}
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export const FranceMap = memo(FranceMapComponent);
