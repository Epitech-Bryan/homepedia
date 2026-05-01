import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
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
import { Skeleton } from "@/components/ui/skeleton";
import "leaflet/dist/leaflet.css";

// Default to a world-level view on first paint. The 4-tier zoom logic in
// PersistentMap switches to the data-rich France stack as soon as the user
// crosses zoom 5, so starting wide keeps the homepage neutral instead of
// pre-biasing to one country.
const INITIAL_CENTER: [number, number] = [20, 10];
const INITIAL_ZOOM = 2;

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

// Hue used for bubbles overlay (warm orange to match the palette).
const ACCENT = "#fc8d59";
const ACCENT_DARK = "#b3502c";
// Stroke colors: dark for active feature, neutral mid-gray for the rest.
const ACTIVE_RING = "#1f2937";
const NEUTRAL_RING = "#7c2d12";
// Fallback fill for polygons that don't have a value for the active metric.
const NO_DATA_FILL = "#e5e7eb";
const NO_DATA_RING = "#9ca3af";

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
  /** Optional weight (e.g. population) used to scale the marker radius. */
  value?: number;
}

export type MapStyle = "choropleth" | "bubbles" | "heat" | "all";

interface FranceMapProps {
  geojson: GeoJSON.FeatureCollection | null;
  /**
   * Optional secondary layer rendered behind {@link geojson}. Used at France
   * zoom (5+) to keep the world country borders visible behind the French
   * regions/departments/cities, so the user always has a sense of where the
   * camera is on the planet. No interaction, no choropleth — just an outline.
   */
  baseGeojson?: GeoJSON.FeatureCollection | null;
  onFeatureClick?: (code: string, name: string) => void;
  markers?: MapMarker[];
  onMarkerClick?: (id: string) => void;
  activeFeatureCode?: string;
  metricByCode?: Record<string, number | null | undefined>;
  metricLabel?: string;
  mapStyle?: MapStyle;
  height?: string;
  onZoomChange?: (zoom: number) => void;
  onCenterChange?: (lat: number, lng: number) => void;
  onBoundsChange?: (south: number, west: number, north: number, east: number) => void;
  /** When true, the map renders edge-to-edge with no rounded corners. */
  bleed?: boolean;
}

function ZoomReporter({ onChange }: { onChange: (z: number) => void }) {
  const map = useMap();
  useEffect(() => {
    onChange(map.getZoom());
    const update = () => onChange(map.getZoom());
    // Listen on `zoom` (fires during zoom) AND `zoomend` for safety.
    map.on("zoom zoomend", update);
    return () => {
      map.off("zoom zoomend", update);
    };
  }, [map, onChange]);
  return null;
}

function CenterReporter({ onChange }: { onChange: (lat: number, lng: number) => void }) {
  const map = useMap();
  useEffect(() => {
    const c = map.getCenter();
    onChange(c.lat, c.lng);
    const update = () => {
      const cc = map.getCenter();
      onChange(cc.lat, cc.lng);
    };
    map.on("moveend", update);
    return () => {
      map.off("moveend", update);
    };
  }, [map, onChange]);
  return null;
}

function BoundsReporter({
  onChange,
}: {
  onChange: (south: number, west: number, north: number, east: number) => void;
}) {
  const map = useMap();
  useEffect(() => {
    const report = () => {
      const b = map.getBounds();
      onChange(b.getSouth(), b.getWest(), b.getNorth(), b.getEast());
    };
    report();
    map.on("moveend zoomend", report);
    return () => {
      map.off("moveend zoomend", report);
    };
  }, [map, onChange]);
  return null;
}

/**
 * Forces Leaflet to remeasure its container when {@code trigger} changes.
 * Without this, resizing the parent (e.g. via an expand button) leaves grey
 * bars on the map until the user pans.
 */
function MapResizer({ trigger }: { trigger: unknown }) {
  const map = useMap();
  useEffect(() => {
    const id = window.setTimeout(() => map.invalidateSize(), 60);
    return () => window.clearTimeout(id);
  }, [trigger, map]);
  return null;
}

/**
 * Closes any sticky tooltip when the user starts panning. Without this,
 * polygons crossed during a drag stack their tooltips on top of each other
 * because mouseover fires on the new polygon before mouseout reaches the
 * previous one.
 */
function DragTooltipHandler() {
  const map = useMap();
  useEffect(() => {
    const closeAll = () => {
      map.eachLayer((layer) => {
        const l = layer as L.Layer & { closeTooltip?: () => void };
        if (typeof l.closeTooltip === "function") {
          l.closeTooltip();
        }
      });
    };
    map.on("dragstart movestart zoomstart", closeAll);
    return () => {
      map.off("dragstart movestart zoomstart", closeAll);
    };
  }, [map]);
  return null;
}

// Cities only become visible past this zoom level so they don't hide the
// choropleth at department/region scale.
const CITY_MARKER_MIN_ZOOM = 10;

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

  // Compute the population range across the visible markers so we can scale
  // each circle proportionally (log scale to handle Paris vs. tiny villages).
  const popRange = useMemo(() => {
    const pops = markers.map((m) => m.value ?? 0).filter((v) => v > 0);
    if (pops.length === 0) return null;
    return { min: Math.log10(Math.min(...pops)), max: Math.log10(Math.max(...pops)) };
  }, [markers]);

  if (zoom < CITY_MARKER_MIN_ZOOM) return null;

  return (
    <>
      {markers.map((m) => {
        const pop = m.value ?? 0;
        let radius = 2.5;
        if (popRange && pop > 0) {
          const ratio = (Math.log10(pop) - popRange.min) / (popRange.max - popRange.min || 1);
          radius = 3 + ratio * 9;
        }
        return (
          <CircleMarker
            key={m.id}
            center={[m.lat, m.lon]}
            radius={radius}
            pathOptions={{
              fillColor: "#1f2937",
              color: "#1f2937",
              fillOpacity: 0.65,
              weight: 0,
            }}
            eventHandlers={{
              click: () => onMarkerClick?.(m.id),
            }}
          >
            <Tooltip sticky direction="top" offset={[0, -4]}>
              <strong>{m.name}</strong>
              {pop > 0 ? (
                <>
                  <br />
                  {pop.toLocaleString("fr-FR")} hab.
                </>
              ) : null}
            </Tooltip>
          </CircleMarker>
        );
      })}
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
  // Track the last fit target so a zoom-driven geojson swap doesn't yank
  // the viewport back to "all France" each time the layer changes.
  const lastFitRef = useRef<string | null>(null);

  useEffect(() => {
    if (!geojson || !geojson.features?.length) return;

    const key = activeFeatureCode ?? "__all__";
    if (lastFitRef.current === key) return;

    let features = geojson.features;
    if (activeFeatureCode) {
      const filtered = features.filter(
        (f) => (f.properties as FeatureProperties | null)?.code === activeFeatureCode,
      );
      if (filtered.length === 0) return; // active code not in this layer; skip
      features = filtered;
    }

    const fc: GeoJSON.FeatureCollection = { type: "FeatureCollection", features };
    const layer = L.geoJSON(fc);
    const bounds = layer.getBounds();
    if (bounds.isValid()) {
      map.flyToBounds(bounds, { padding: [32, 32], duration: 0.7 });
      lastFitRef.current = key;
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
  baseGeojson,
  onFeatureClick,
  markers,
  onMarkerClick,
  activeFeatureCode,
  metricByCode,
  metricLabel,
  mapStyle = "choropleth",
  height = "500px",
  onZoomChange,
  onCenterChange,
  onBoundsChange,
  bleed = false,
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
      const hasData = !!choroplethColor;

      return {
        fillColor: choroplethColor ?? NO_DATA_FILL,
        fillOpacity: isActive ? 0.92 : hasData ? 0.78 : 0.5,
        color: isActive ? ACTIVE_RING : hasData ? NEUTRAL_RING : NO_DATA_RING,
        weight: isActive ? 2.5 : 0.9,
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
          target.setStyle({
            fillOpacity: hasChoropleth ? 0.95 : 0.7,
            weight: 2.5,
            color: ACTIVE_RING,
          });
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

  // layerKey forces LeafletGeoJSON to remount when the underlying data or the
  // styling axis changes. The previous version stringified geojson on every
  // render, which was both slow AND noisy: any feature add/remove changed
  // the truncated prefix, remounting the layer. Identity (geojson reference)
  // + feature count is enough — TanStack Query keeps the same reference for
  // cached data, so panning across already-loaded depts no longer remounts.
  const layerKey = useMemo(() => {
    const featureCount = geojson?.features.length ?? 0;
    const metricKey = metricByCode
      ? Object.keys(metricByCode).length + ":" + JSON.stringify(choroplethRange)
      : "no-metric";
    return `${featureCount}|${metricKey}|${activeFeatureCode ?? ""}`;
  }, [geojson, metricByCode, choroplethRange, activeFeatureCode]);

  return (
    <div
      className={
        bleed
          ? "h-full overflow-hidden border-y border-border/60 bg-background"
          : "h-full overflow-hidden rounded-lg border border-border/60 bg-background shadow-sm"
      }
    >
      <div className="h-full p-0">
        <div
          className="relative h-full"
          style={{ minHeight: height === "100%" ? undefined : height, height }}
        >
          {!geojson ? (
            <Skeleton className="h-full w-full" />
          ) : (
            <>
              <MapContainer
                center={INITIAL_CENTER}
                zoom={INITIAL_ZOOM}
                minZoom={2}
                scrollWheelZoom={true}
                zoomControl={false}
                // worldCopyJump teleports the camera back across the
                // antimeridian as the user pans, so horizontal scroll feels
                // continuous instead of slamming into a wall at the
                // dateline. The country GeoJSON is itself duplicated at
                // ±360° in PersistentMap so borders render on every copy
                // of the world the tile layer paints.
                worldCopyJump={true}
                // preferCanvas: render polygons + circles via Canvas instead
                // of SVG. At city zoom we display thousands of commune
                // polygons; SVG creates one DOM node per shape and the
                // browser layout cost dominates on every pan. Canvas drops
                // pan latency by ~3-5x.
                preferCanvas={true}
                style={{ width: "100%", height: "100%", background: "#f4f1ec" }}
              >
                <TileLayer
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
                  url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager_nolabels/{z}/{x}/{y}{r}.png"
                  subdomains="abcd"
                  maxZoom={20}
                />
                {baseGeojson && (
                  <LeafletGeoJSON
                    // Grey-out world country outlines as a backdrop. No
                    // hover, no choropleth, no events — purely contextual.
                    // Distinct key so React doesn't try to reconcile it with
                    // the foreground layer when zoom switches.
                    key={`base-${baseGeojson.features.length}`}
                    data={baseGeojson}
                    style={{
                      color: "#94a3b8",
                      weight: 0.5,
                      fillColor: "#cbd5e1",
                      fillOpacity: 0.18,
                      interactive: false,
                    }}
                  />
                )}
                {/* In heat and bubbles modes the polygon borders compete
                    visually with the gradient/markers — drop the foreground
                    GeoJSON layer entirely. Bubbles carry their own click
                    + tooltip so interaction still works. The grey country
                    backdrop above keeps geographic context. choropleth +
                    "all" modes keep the polygons so hover/click + the
                    choropleth fill keep working. */}
                {mapStyle !== "heat" && mapStyle !== "bubbles" && (
                  <LeafletGeoJSON
                    key={layerKey}
                    data={geojson}
                    style={baseStyle}
                    onEachFeature={onEachFeature}
                  />
                )}
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
                {onZoomChange && <ZoomReporter onChange={onZoomChange} />}
                {onCenterChange && <CenterReporter onChange={onCenterChange} />}
                {onBoundsChange && <BoundsReporter onChange={onBoundsChange} />}
                <MapResizer trigger={height} />
                <DragTooltipHandler />
                <FitBounds geojson={geojson} activeFeatureCode={activeFeatureCode} />
              </MapContainer>
              {showChoropleth && choroplethRange && (
                <Legend range={choroplethRange} label={metricLabel} />
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export const FranceMap = memo(FranceMapComponent);
