import { memo, useCallback, useEffect, useMemo } from "react";
import {
  MapContainer,
  TileLayer,
  GeoJSON as LeafletGeoJSON,
  CircleMarker,
  Tooltip,
  useMap,
} from "react-leaflet";
import L from "leaflet";
import type { Layer, LeafletMouseEvent, PathOptions } from "leaflet";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import "leaflet/dist/leaflet.css";

const FRANCE_CENTER: [number, number] = [46.6, 2.5];
const FRANCE_ZOOM = 6;

const CHOROPLETH_SCALE = ["#eff6ff", "#bfdbfe", "#60a5fa", "#2563eb", "#1e3a8a"];

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

export type MapStyle = "choropleth" | "bubbles" | "both";

interface FranceMapProps {
  geojson: GeoJSON.FeatureCollection | null;
  onFeatureClick?: (code: string, name: string) => void;
  markers?: MapMarker[];
  onMarkerClick?: (id: string) => void;
  activeFeatureCode?: string;
  metricByCode?: Record<string, number | null | undefined>;
  mapStyle?: MapStyle;
  fillColor?: string;
  height?: string;
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
      map.flyToBounds(bounds, { padding: [24, 24], duration: 0.6 });
    }
  }, [geojson, activeFeatureCode, map]);
  return null;
}

function FranceMapComponent({
  geojson,
  onFeatureClick,
  markers,
  onMarkerClick,
  activeFeatureCode,
  metricByCode,
  mapStyle = "choropleth",
  fillColor = "hsl(221.2 83.2% 53.3%)",
  height = "500px",
}: FranceMapProps) {
  const showChoropleth = mapStyle === "choropleth" || mapStyle === "both";
  const showBubbles = mapStyle === "bubbles" || mapStyle === "both";
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
    (code: string | undefined): string => {
      if (!code || !metricByCode || !choroplethRange || !showChoropleth) return fillColor;
      const value = metricByCode[code];
      if (value == null || !Number.isFinite(value) || value <= 0) return "#e5e7eb";
      const ratio = (value - choroplethRange.min) / (choroplethRange.max - choroplethRange.min);
      const idx = Math.min(
        CHOROPLETH_SCALE.length - 1,
        Math.floor(ratio * CHOROPLETH_SCALE.length),
      );
      return CHOROPLETH_SCALE[idx];
    },
    [metricByCode, choroplethRange, fillColor, showChoropleth],
  );

  const bubbles = useMemo(() => {
    if (!showBubbles || !geojson || !metricByCode || !choroplethRange) return [];
    const result: Array<{
      code: string;
      name: string;
      lat: number;
      lng: number;
      value: number;
      radius: number;
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
      const radius = 6 + ratio * 22;
      result.push({
        code,
        name: props?.name ?? props?.nom ?? "",
        lat: center.lat,
        lng: center.lng,
        value,
        radius,
      });
    }
    return result;
  }, [geojson, metricByCode, choroplethRange, showBubbles]);

  const baseStyle = useCallback(
    (feature?: GeoJSON.Feature<GeoJSON.Geometry, FeatureProperties>): PathOptions => {
      const code = feature?.properties?.code;
      const color = colorForCode(code);
      const isActive = activeFeatureCode && code === activeFeatureCode;
      return {
        fillColor: color,
        fillOpacity: isActive ? 0.7 : 0.55,
        color: color,
        weight: isActive ? 2.5 : 1.2,
        opacity: 0.9,
      };
    },
    [colorForCode, activeFeatureCode],
  );

  const onEachFeature = useCallback(
    (feature: GeoJSON.Feature<GeoJSON.Geometry, FeatureProperties>, layer: Layer) => {
      const props = feature.properties;
      const name = props?.name ?? props?.nom ?? "";
      const code = props?.code ?? "";
      const isActive = activeFeatureCode && code === activeFeatureCode;
      const value = metricByCode?.[code];

      const tooltip =
        value != null && Number.isFinite(value)
          ? `${name} — ${value.toLocaleString("fr-FR")}`
          : name;
      if (tooltip) {
        layer.bindTooltip(tooltip, { sticky: true });
      }

      layer.on({
        mouseover: (e: LeafletMouseEvent) => {
          const target = e.target as L.Path;
          target.setStyle({ fillOpacity: 0.85, weight: 3 });
          target.bringToFront();
        },
        mouseout: (e: LeafletMouseEvent) => {
          const target = e.target as L.Path;
          target.setStyle(baseStyle(feature));
        },
        click: () => {
          if (onFeatureClick && code) {
            onFeatureClick(code, name);
          }
        },
      });

      // Force initial style
      (layer as L.Path).setStyle(isActive ? baseStyle(feature) : baseStyle(feature));
    },
    [onFeatureClick, activeFeatureCode, metricByCode, baseStyle],
  );

  const layerKey = useMemo(() => {
    const sample = geojson ? JSON.stringify(geojson).slice(0, 80) : "";
    const metricKey = metricByCode
      ? Object.keys(metricByCode).length + ":" + JSON.stringify(choroplethRange)
      : "no-metric";
    return `${sample}|${metricKey}`;
  }, [geojson, metricByCode, choroplethRange]);

  return (
    <Card className="overflow-hidden">
      <CardContent className="p-0">
        <div style={{ height }}>
          {!geojson ? (
            <Skeleton className="w-full h-full" />
          ) : (
            <MapContainer
              center={FRANCE_CENTER}
              zoom={FRANCE_ZOOM}
              scrollWheelZoom={true}
              style={{ width: "100%", height: "100%" }}
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
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
                    fillColor: "hsl(221.2 83.2% 53.3%)",
                    color: "hsl(221.2 83.2% 33%)",
                    fillOpacity: 0.55,
                    weight: 1.5,
                  }}
                  eventHandlers={{
                    click: () => onFeatureClick?.(b.code, b.name),
                  }}
                >
                  <Tooltip sticky>
                    {b.name} — {b.value.toLocaleString("fr-FR")}
                  </Tooltip>
                </CircleMarker>
              ))}
              {markers?.map((m) => (
                <CircleMarker
                  key={m.id}
                  center={[m.lat, m.lon]}
                  radius={4}
                  pathOptions={{
                    fillColor: "hsl(0 72% 51%)",
                    color: "hsl(0 72% 51%)",
                    fillOpacity: 0.85,
                    weight: 1,
                  }}
                  eventHandlers={{
                    click: () => onMarkerClick?.(m.id),
                  }}
                >
                  <Tooltip sticky>{m.name}</Tooltip>
                </CircleMarker>
              ))}
              <FitBounds geojson={geojson} activeFeatureCode={activeFeatureCode} />
            </MapContainer>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export const FranceMap = memo(FranceMapComponent);
