import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  MapContainer,
  TileLayer,
  GeoJSON as LeafletGeoJSON,
  CircleMarker,
  Popup,
  Tooltip,
  useMap,
} from "react-leaflet";
import L from "leaflet";
import "leaflet.heat";
import type { Layer, LeafletMouseEvent, PathOptions } from "leaflet";
import { Skeleton } from "@/components/ui/skeleton";
import { CityVectorGridLayer } from "@/components/CityVectorGridLayer";
import { WorldVectorGridLayer } from "@/components/WorldVectorGridLayer";
import { useComparableSales } from "@/api/hooks";
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
  onFeatureClick?: (code: string, name?: string) => void;
  markers?: MapMarker[];
  onMarkerClick?: (id: string) => void;
  activeFeatureCode?: string;
  metricByCode?: Record<string, number | null | undefined>;
  /**
   * When set, the vector-tile commune layer prefers values read from the
   * MVT feature properties over {@link metricByCode}. Lets the choropleth
   * colour each polygon from tile-baked stats (population, avg €/m², …)
   * with zero {@code /stats/cities} round-trips.
   */
  metricFromFeature?: (props: Record<string, unknown>) => number | null | undefined;
  /**
   * Overrides the choropleth min/max range that would otherwise be derived
   * from {@link metricByCode}. Used when the commune values aren't in
   * {@link metricByCode} but in tile feature properties — the global
   * min/max + 6 quantile breakpoints come pre-computed from the backend's
   * {@code /api/tiles/cities/metric-ranges} endpoint so the gradient bins
   * communes by rank rather than by raw value (Paris doesn't wash out the
   * rest of France).
   */
  choroplethRange?: { min: number; max: number; breaks?: number[] } | null;
  metricLabel?: string;
  /**
   * When provided, the heat layer feeds directly off these per-address
   * points (latitude / longitude / value) instead of falling back to the
   * polygon-shape sampling. Comes from the backend heatpoints endpoint
   * and is only fetched at city zoom where individual transactions are
   * meaningful.
   */
  precisionHeatPoints?: Array<{ latitude: number; longitude: number; value: number }>;
  /**
   * Individual geocoded transactions to render as clickable pins over the
   * heatmap. Same source data as {@link precisionHeatPoints} but
   * un-bucketed, with the per-mutation metadata the popup needs.
   */
  transactionMarkers?: Array<{
    id: number;
    latitude: number;
    longitude: number;
    propertyValue: number;
    propertyType: string;
    builtSurface: number | null;
    roomCount: number | null;
    mutationDate: string;
  }>;
  /**
   * When true, render the commune polygons through a vector-tile layer
   * (issue #6) instead of relying on the geojson prop. The caller is
   * still expected to pass {@code geojson=null} or a non-commune
   * collection to avoid double-drawing; this prop only governs whether
   * the VectorGrid overlay mounts.
   */
  useVectorTilesForCities?: boolean;
  /**
   * When true, render the world layers (countries, admin-1, admin-2)
   * through the {@code /api/tiles/world} MVT pipeline instead of the
   * SVG GeoJSON path. Behind a feature flag so the rollout can be
   * staged: SVG keeps working until the world.mbtiles is present in
   * the PVC.
   */
  useVectorTilesForWorld?: boolean;
  mapStyle?: MapStyle;
  height?: string;
  onZoomChange?: (zoom: number) => void;
  onCenterChange?: (lat: number, lng: number) => void;
  onBoundsChange?: (south: number, west: number, north: number, east: number) => void;
  /** When true, the map renders edge-to-edge with no rounded corners. */
  bleed?: boolean;
}

/**
 * Backdrop world-country outlines with a stroke weight that thickens as the
 * user zooms in. The previous fixed {@code weight: 0.5} disappeared past
 * region zoom — country boundaries are still the relevant geographic
 * context when you're staring at French communes; scaling avoids losing
 * them while staying light enough not to compete with the foreground at
 * world zoom.
 */
function BackdropCountriesLayer({ data }: { data: GeoJSON.FeatureCollection }) {
  const map = useMap();
  const layerRef = useRef<L.GeoJSON | null>(null);
  const weightForZoom = (z: number): number => {
    // Linear from 0.5 at world (z=2) to ~2.5 at city zoom (z=14), capped
    // either side. Hand-tuned so the borders read on a 13" laptop without
    // turning into a thick line that distracts from the choropleth.
    if (z <= 4) return 0.5;
    if (z >= 13) return 2.5;
    return 0.5 + (z - 4) * (2 / 9);
  };
  // Dedicated pane so the backdrop SVG never intercepts events. The default
  // overlayPane (z 400) sits above the tilePane (z 200) where the MVT
  // commune layer lives, and even with `interactive: false` on each path
  // the parent <svg> stays `pointer-events: auto` and swallows mousemove
  // — breaking hover on the vector-tile choropleth underneath. Setting
  // `pointer-events: none` on the pane itself lets every event fall
  // through to the canvas below regardless of z-order.
  //
  // Created synchronously in render (idempotent via getPane check) — a
  // useEffect runs AFTER the child <LeafletGeoJSON pane="backdrop"> has
  // mounted, leaving Leaflet with no renderer for the path. The onRemove
  // cleanup then crashes with "Cannot read properties of undefined
  // (reading '_removePath')" because the ad-hoc renderer Leaflet allocated
  // gets garbage-collected before the layer's teardown runs.
  if (!map.getPane("backdrop")) {
    const pane = map.createPane("backdrop");
    pane.style.zIndex = "350";
    pane.style.pointerEvents = "none";
  }
  useEffect(() => {
    const update = () => {
      const layer = layerRef.current;
      if (layer) layer.setStyle({ weight: weightForZoom(map.getZoom()) });
    };
    map.on("zoomend", update);
    return () => {
      map.off("zoomend", update);
    };
  }, [map]);
  return (
    <LeafletGeoJSON
      ref={(r) => {
        layerRef.current = r;
      }}
      // Grey-out world country outlines as a backdrop. No hover, no
      // choropleth, no events — purely contextual.
      key={`base-${data.features.length}`}
      data={data}
      pane="backdrop"
      style={{
        color: "#64748b",
        weight: weightForZoom(map.getZoom()),
        fillColor: "#cbd5e1",
        fillOpacity: 0.18,
        interactive: false,
      }}
    />
  );
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

// Per-transaction pins layer. Colour scales with €/m² when the marker has
// a built surface, falls back to a neutral pin otherwise. Popup is built
// inline to keep the layer self-contained — the data already includes
// everything we need to render it.
function TransactionMarkerLayer({
  markers,
}: {
  markers: NonNullable<FranceMapProps["transactionMarkers"]>;
}) {
  // Derive a €/m² range across the visible markers so the colour ramp
  // adapts to the local price scale instead of using one fixed nationwide
  // scale (a marker at 5 k€/m² in Limoges should still look "hot").
  const priceRange = useMemo(() => {
    const prices: number[] = [];
    for (const m of markers) {
      if (m.builtSurface && m.builtSurface > 0 && m.propertyValue > 0) {
        prices.push(m.propertyValue / m.builtSurface);
      }
    }
    if (prices.length === 0) return null;
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    if (min === max) return null;
    return { min, max };
  }, [markers]);

  const colorFor = useCallback(
    (m: (typeof markers)[number]): string => {
      if (!priceRange || !m.builtSurface || m.builtSurface <= 0) return "#374151";
      const pricePerSqm = m.propertyValue / m.builtSurface;
      const ratio = (pricePerSqm - priceRange.min) / (priceRange.max - priceRange.min);
      const idx = Math.min(
        CHOROPLETH_SCALE.length - 1,
        Math.floor(ratio * CHOROPLETH_SCALE.length),
      );
      return CHOROPLETH_SCALE[idx];
    },
    [priceRange],
  );

  return (
    <>
      {markers.map((m) => {
        const fill = colorFor(m);
        return (
          <CircleMarker
            key={m.id}
            center={[m.latitude, m.longitude]}
            radius={5}
            pathOptions={{
              fillColor: fill,
              color: "#1f2937",
              fillOpacity: 0.85,
              weight: 0.6,
            }}
          >
            <Popup>
              <TransactionPopupContent marker={m} />
            </Popup>
          </CircleMarker>
        );
      })}
    </>
  );
}

/**
 * Popup body for a single transaction marker. Lives as its own component
 * so the comparable-sales hook is only triggered when the popup is open
 * (react-leaflet defers children render until then) — otherwise we'd fire
 * a `/comparable-sales` request for every visible marker on every pan.
 */
function TransactionPopupContent({
  marker: m,
}: {
  marker: NonNullable<FranceMapProps["transactionMarkers"]>[number];
}) {
  const map = useMap();
  const [showComparables, setShowComparables] = useState(false);
  const { data: comparables, isLoading } = useComparableSales(showComparables ? m.id : null);

  const pricePerSqm =
    m.builtSurface && m.builtSurface > 0 ? Math.round(m.propertyValue / m.builtSurface) : null;
  const dateLabel = new Date(m.mutationDate).toLocaleDateString("fr-FR", {
    year: "numeric",
    month: "long",
  });

  return (
    <div className="text-xs leading-tight">
      <div className="font-semibold">
        {m.propertyValue.toLocaleString("fr-FR")} €
        {pricePerSqm != null && (
          <span className="ml-1 font-normal text-muted-foreground">
            ({pricePerSqm.toLocaleString("fr-FR")} €/m²)
          </span>
        )}
      </div>
      <div className="mt-1 text-muted-foreground">
        {[
          m.propertyType || null,
          m.builtSurface ? `${Math.round(m.builtSurface)} m²` : null,
          m.roomCount ? `${m.roomCount}p` : null,
        ]
          .filter(Boolean)
          .join(" · ")}
      </div>
      <div className="text-muted-foreground">{dateLabel}</div>

      {!showComparables ? (
        <button
          type="button"
          onClick={() => setShowComparables(true)}
          className="mt-2 text-[11px] font-medium text-primary hover:underline"
        >
          Voir 10 ventes similaires →
        </button>
      ) : (
        <div className="mt-2 border-t pt-2">
          <div className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
            Ventes comparables
          </div>
          {isLoading && <div className="mt-1 text-muted-foreground">Chargement…</div>}
          {!isLoading && (!comparables || comparables.length === 0) && (
            <div className="mt-1 text-muted-foreground">
              Pas encore d'agrégat — le job Spark n'a pas tourné sur cette mutation.
            </div>
          )}
          {!isLoading && comparables && comparables.length > 0 && (
            <ul className="mt-1 max-h-40 overflow-y-auto divide-y">
              {comparables.map((c) => {
                const cPricePerSqm =
                  c.builtSurface && c.builtSurface > 0
                    ? Math.round(c.propertyValue / c.builtSurface)
                    : null;
                return (
                  <li
                    key={c.comparableId}
                    className="py-1 cursor-pointer hover:bg-muted/40 rounded px-1"
                    onClick={() =>
                      map.flyTo([c.latitude, c.longitude], Math.max(map.getZoom(), 14))
                    }
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-medium">
                        {c.propertyValue.toLocaleString("fr-FR")} €
                      </span>
                      <span
                        className={
                          c.priceDeltaPct >= 0
                            ? "text-[10px] font-medium text-emerald-700"
                            : "text-[10px] font-medium text-red-700"
                        }
                      >
                        {c.priceDeltaPct >= 0 ? "+" : ""}
                        {c.priceDeltaPct.toFixed(1)} %
                      </span>
                    </div>
                    <div className="text-[10px] text-muted-foreground">
                      {cPricePerSqm != null && `${cPricePerSqm.toLocaleString("fr-FR")} €/m² · `}
                      {c.distanceM < 1000
                        ? `${c.distanceM} m`
                        : `${(c.distanceM / 1000).toFixed(1)} km`}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </div>
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
  metricFromFeature,
  choroplethRange: choroplethRangeOverride,
  metricLabel,
  precisionHeatPoints,
  transactionMarkers,
  useVectorTilesForCities = false,
  useVectorTilesForWorld = false,
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
    if (choroplethRangeOverride) return choroplethRangeOverride;
    if (!metricByCode) return null;
    const values = Object.values(metricByCode)
      .filter((v): v is number => typeof v === "number" && Number.isFinite(v) && v > 0)
      .sort((a, b) => a - b);
    if (values.length === 0) return null;
    const min = values[0];
    const max = values[values.length - 1];
    if (min === max) return null;
    // Compute the same 6 quantile cuts as CityTileBuilder so SVG layers
    // (regions / departments) get binned the same way as the MVT commune
    // layer — keeps the gradient consistent when the user zooms across
    // levels and stops Paris / Île-de-France from dominating the scale.
    const breaks =
      values.length >= 7
        ? Array.from(
            { length: 6 },
            (_, i) => values[Math.round(((i + 1) / 7) * (values.length - 1))],
          )
        : [];
    return { min, max, breaks };
  }, [choroplethRangeOverride, metricByCode]);

  const colorForCode = useCallback(
    (code: string | undefined): string | null => {
      if (!code || !metricByCode || !choroplethRange || !showChoropleth) return null;
      const value = metricByCode[code];
      if (value == null || !Number.isFinite(value) || value <= 0) return null;
      const breaks = choroplethRange.breaks;
      let idx: number;
      if (breaks && breaks.length > 0) {
        idx = breaks.length;
        for (let i = 0; i < breaks.length; i++) {
          if (value <= breaks[i]) {
            idx = i;
            break;
          }
        }
        idx = Math.min(CHOROPLETH_SCALE.length - 1, idx);
      } else {
        const ratio = (value - choroplethRange.min) / (choroplethRange.max - choroplethRange.min);
        idx = Math.min(CHOROPLETH_SCALE.length - 1, Math.floor(ratio * CHOROPLETH_SCALE.length));
      }
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
    // Highest precision: per-address aggregates fetched from the backend
    // heatpoints endpoint. Each row is a ~100 m bucket of geocoded
    // transactions with the metric value already averaged in. Normalise
    // by the max so the heatmap kernel weights the hot spots correctly.
    if (precisionHeatPoints && precisionHeatPoints.length > 0) {
      let maxV = 0;
      for (const p of precisionHeatPoints) if (p.value > maxV) maxV = p.value;
      if (maxV <= 0) return [];
      return precisionHeatPoints.map(
        (p) => [p.latitude, p.longitude, p.value / maxV] as L.HeatLatLngTuple,
      );
    }
    if (markers && markers.length > 0) {
      return markers.map((m) => [m.lat, m.lon, 1] as L.HeatLatLngTuple);
    }
    if (!geojson || !metricByCode || !choroplethRange) return [];
    // Walk each polygon's outer ring at regular intervals and add a
    // centroid — every point is by construction on or inside the
    // feature, no random jitter, no rejection sampling. Larger features
    // (and features with a high metric value) emit more samples, so a
    // métropole like Lille paints a continuous blob that follows the
    // commune's actual shape and merges with its neighbours instead of
    // collapsing on a single centroid.
    const MAX_POINTS_PER_PART = 24;
    const points: L.HeatLatLngTuple[] = [];
    const pushRingSamples = (ring: number[][], ratio: number, count: number) => {
      if (ring.length < 3 || count <= 0) return;
      const step = ring.length / count;
      for (let i = 0; i < count; i++) {
        const idx = Math.min(ring.length - 1, Math.floor(i * step));
        const [lng, lat] = ring[idx];
        points.push([lat, lng, ratio] as L.HeatLatLngTuple);
      }
    };
    const ringCentroid = (ring: number[][]): [number, number] => {
      let sx = 0;
      let sy = 0;
      const n = ring.length - 1; // last vertex usually equals the first
      for (let i = 0; i < n; i++) {
        sx += ring[i][0];
        sy += ring[i][1];
      }
      return [sy / n, sx / n];
    };
    for (const feature of geojson.features) {
      const props = feature.properties as FeatureProperties | null;
      const code = props?.code;
      if (!code) continue;
      const value = metricByCode[code];
      if (value == null || !Number.isFinite(value) || value <= 0) continue;
      const ratio = Math.max(
        0,
        Math.min(1, (value - choroplethRange.min) / (choroplethRange.max - choroplethRange.min)),
      );
      const polys: number[][][][] = [];
      const g = feature.geometry;
      if (g.type === "Polygon") polys.push(g.coordinates);
      else if (g.type === "MultiPolygon") polys.push(...g.coordinates);
      else continue;
      for (const poly of polys) {
        const outer = poly[0];
        if (!outer || outer.length < 3) continue;
        // Per-part sample count grows with the metric ratio. Tiny
        // contributions stay at 1 sample so they don't drop out entirely.
        const count = Math.max(1, Math.round(ratio * MAX_POINTS_PER_PART));
        pushRingSamples(outer, ratio, count);
        // One interior anchor so the kernel actually fills the polygon
        // instead of just outlining its boundary.
        const [cy, cx] = ringCentroid(outer);
        points.push([cy, cx, ratio] as L.HeatLatLngTuple);
      }
    }
    return points;
  }, [showHeat, precisionHeatPoints, markers, geojson, metricByCode, choroplethRange]);

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
          {!geojson && !useVectorTilesForCities ? (
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
                {baseGeojson && <BackdropCountriesLayer data={baseGeojson} />}
                {/* In heat and bubbles modes the polygon borders compete
                    visually with the gradient/markers — drop the foreground
                    GeoJSON layer entirely. Bubbles carry their own click
                    + tooltip so interaction still works. The grey country
                    backdrop above keeps geographic context. choropleth +
                    "all" modes keep the polygons so hover/click + the
                    choropleth fill keep working. */}
                {geojson && mapStyle !== "heat" && mapStyle !== "bubbles" && (
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
                {transactionMarkers && transactionMarkers.length > 0 && (
                  <TransactionMarkerLayer markers={transactionMarkers} />
                )}
                {/* World layer must mount BEFORE the city layer so the
                    latter ends up on top in Leaflet's stacking order.
                    Without this, at z=9-10 (where the city MVT band
                    z=9-14 overlaps the world admin-2 band z=8-10) the
                    World canvas eats every click — even over France
                    where the world tile is empty — because Leaflet
                    routes events to the last-added layer first. */}
                {useVectorTilesForWorld && (
                  <WorldVectorGridLayer
                    metricFromFeature={metricFromFeature}
                    range={choroplethRange}
                    palette={CHOROPLETH_SCALE}
                    onFeatureClick={onFeatureClick}
                    metricLabel={metricLabel}
                  />
                )}
                {useVectorTilesForCities && (
                  <CityVectorGridLayer
                    metricFromFeature={metricFromFeature}
                    metricByCode={metricByCode}
                    range={choroplethRange}
                    palette={CHOROPLETH_SCALE}
                    onFeatureClick={onFeatureClick}
                    metricLabel={metricLabel}
                  />
                )}
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
