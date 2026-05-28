import { useEffect, useRef } from "react";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import type { MapStyle } from "./FranceMap";

type MapMetricKey =
  | "population"
  | "density"
  | "gdpPerCapita"
  | "averagePrice"
  | "averagePricePerSqm"
  | "transactionCount"
  | "pollution";

interface FranceMapGLProps {
  metricKey?: MapMetricKey;
  metricByCode?: Record<string, number | null | undefined>;
  metricFromFeature?: (props: Record<string, unknown>) => number | null | undefined;
  choroplethRange?: { min: number; max: number; breaks?: number[] } | null;
  metricLabel?: string;
  onFeatureClick?: (code: string, name?: string) => void;
  activeFeatureCode?: string;
  basemap?: "voyager" | "satellite";
  mapStyle?: MapStyle;
  height?: string;
  onZoomChange?: (zoom: number) => void;
  onCenterChange?: (lat: number, lng: number) => void;
  onBoundsChange?: (south: number, west: number, north: number, east: number) => void;
}

const CHOROPLETH_SCALE = [
  "#fef0d9",
  "#fdd49e",
  "#fdbb84",
  "#fc8d59",
  "#ef6548",
  "#d7301f",
  "#990000",
];
const NO_DATA_FILL = "#e5e7eb";
const HOVER_LINE = "#1f2937";

const LAYER_DEFS = [
  { id: "w-admin1-fill", source: "world", sourceLayer: "admin1", minzoom: 0, maxzoom: 22 },
  { id: "c-cities-fill", source: "cities", sourceLayer: "cities", minzoom: 8, maxzoom: 22 },
] as const;

const RASTER_TILES: Record<string, string[]> = {
  voyager: [
    "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png",
    "https://b.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png",
    "https://c.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png",
    "https://d.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png",
  ],
  satellite: [
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
  ],
};

type Expr = unknown[];

function numProp(prop: string): Expr {
  return ["to-number", ["get", prop], 0];
}

function areaExpr(): Expr {
  return ["max", ["to-number", ["coalesce", ["get", "areaKm2"], ["get", "area"], 0], 0], 0];
}

function metricValueExpr(metric: MapMetricKey): Expr {
  switch (metric) {
    case "density":
      return ["case", [">", areaExpr(), 0], ["/", numProp("population"), areaExpr()], 0];
    case "gdpPerCapita":
      return numProp("gdpPerCapita");
    case "pollution":
      return numProp("pollutionScore");
    default:
      return numProp(metric);
  }
}

function metricHasValueExpr(metric: MapMetricKey): Expr {
  switch (metric) {
    case "density":
      return ["all", ["!=", ["get", "population"], ["literal", null]], [">", areaExpr(), 0]];
    case "gdpPerCapita":
      return ["!=", ["get", "gdpPerCapita"], ["literal", null]];
    case "pollution":
      return ["!=", ["get", "pollutionScore"], ["literal", null]];
    default:
      return ["!=", ["get", metric], ["literal", null]];
  }
}

function deriveRange(values: Array<number | null | undefined>): {
  min: number;
  max: number;
  breaks: number[];
} | null {
  const nums = values
    .filter((v): v is number => typeof v === "number" && Number.isFinite(v))
    .sort((a, b) => a - b);
  if (nums.length < 2) return null;
  const min = nums[0];
  const max = nums[nums.length - 1];
  if (max <= min) return null;
  const breaks: number[] = [];
  for (let i = 1; i <= 6; i++) {
    const q = nums[Math.floor((nums.length - 1) * (i / 7))];
    breaks.push(q);
  }
  return { min, max, breaks };
}

function colorExpr(
  metric: MapMetricKey,
  range: { min: number; max: number; breaks?: number[] } | null,
): Expr {
  if (!range || range.max <= range.min) return ["literal", NO_DATA_FILL];
  const value = metricValueExpr(metric);
  let ramp: Expr;
  const ascendingBreaks = (range.breaks ?? []).filter(
    (b, i, arr) => Number.isFinite(b) && (i === 0 || b > arr[i - 1]),
  );
  if (ascendingBreaks.length > 0) {
    const stops: unknown[] = ["step", value, CHOROPLETH_SCALE[0]];
    ascendingBreaks.forEach((b, i) => {
      stops.push(b, CHOROPLETH_SCALE[Math.min(i + 1, CHOROPLETH_SCALE.length - 1)]);
    });
    ramp = stops;
  } else {
    const stops: unknown[] = ["interpolate", ["linear"], value];
    CHOROPLETH_SCALE.forEach((c, i) => {
      stops.push(range.min + ((range.max - range.min) * i) / (CHOROPLETH_SCALE.length - 1), c);
    });
    ramp = stops;
  }
  return ["case", metricHasValueExpr(metric), ramp, NO_DATA_FILL];
}

export default function FranceMapGL({
  metricKey = "population",
  metricFromFeature,
  metricLabel,
  onFeatureClick,
  basemap = "voyager",
  height = "100%",
  onZoomChange,
  onCenterChange,
  onBoundsChange,
}: FranceMapGLProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const popupRef = useRef<maplibregl.Popup | null>(null);
  const hoveredRef = useRef<{ source: string; sourceLayer: string; id: string } | null>(null);
  const cbRef = useRef({
    onZoomChange,
    onCenterChange,
    onBoundsChange,
    onFeatureClick,
    metricFromFeature,
    metricLabel,
    metricKey,
  });
  useEffect(() => {
    cbRef.current = {
      onZoomChange,
      onCenterChange,
      onBoundsChange,
      onFeatureClick,
      metricFromFeature,
      metricLabel,
      metricKey,
    };
  });

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      center: [10, 20],
      zoom: 2,
      minZoom: 0.5,
      maxZoom: 19,
      attributionControl: { compact: true },
      style: {
        version: 8,
        projection: { type: "globe" },
        glyphs: "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
        sources: {
          basemap: {
            type: "raster",
            tiles: RASTER_TILES[basemap],
            tileSize: 256,
            attribution: "&copy; OSM &copy; CARTO / Esri",
          },
          world: {
            type: "vector",
            tiles: [`${location.origin}/api/tiles/world/{z}/{x}/{y}.pbf`],
            minzoom: 0,
            maxzoom: 12,
            promoteId: "code",
          },
          cities: {
            type: "vector",
            tiles: [`${location.origin}/api/tiles/cities/{z}/{x}/{y}.pbf`],
            minzoom: 4,
            maxzoom: 14,
            promoteId: "code",
          },
        },
        layers: [{ id: "basemap", type: "raster", source: "basemap" }],
      },
    });
    mapRef.current = map;
    map.addControl(
      new maplibregl.NavigationControl({ showCompass: true, visualizePitch: true }),
      "bottom-right",
    );
    map.dragRotate.disable();
    map.touchZoomRotate.disableRotation();

    const ro = new ResizeObserver(() => map.resize());
    ro.observe(containerRef.current);
    map.once("load", () => {
      map.resize();
      map.easeTo({ zoom: map.getZoom() + 0.001, duration: 0 });
      map.triggerRepaint();
    });
    const kick = window.setTimeout(() => {
      map.resize();
      map.triggerRepaint();
    }, 350);

    popupRef.current = new maplibregl.Popup({
      closeButton: false,
      closeOnClick: false,
      className: "maplibre-hover-popup",
    });

    map.on("style.load", () => {
      map.setSky({
        "sky-color": "#9cc6ff",
        "sky-horizon-blend": 0.5,
        "horizon-color": "#ffffff",
        "horizon-fog-blend": 0.5,
        "fog-color": "#dfe9f5",
        "fog-ground-blend": 0.6,
      });
    });

    const emit = () => {
      const z = map.getZoom();
      const c = map.getCenter();
      const b = map.getBounds();
      cbRef.current.onZoomChange?.(z);
      cbRef.current.onCenterChange?.(c.lat, c.lng);
      cbRef.current.onBoundsChange?.(b.getSouth(), b.getWest(), b.getNorth(), b.getEast());
    };
    map.on("moveend", emit);
    map.on("load", emit);
    map.on("idle", () => recolor(map, cbRef.current.metricKey));

    const FILL_LAYERS = ["c-cities-fill", "w-admin1-fill"];

    const clearHover = () => {
      if (hoveredRef.current) {
        map.setFeatureState(hoveredRef.current, { hover: false });
        hoveredRef.current = null;
      }
      popupRef.current?.remove();
      map.getCanvas().style.cursor = "";
    };

    map.on("mousemove", (e) => {
      const feats = map.queryRenderedFeatures(e.point, {
        layers: FILL_LAYERS.filter((l) => map.getLayer(l)),
      });
      if (!feats.length) {
        clearHover();
        return;
      }
      const f = feats[0];
      const code = String(f.properties?.code ?? "");
      const src = f.source as string;
      const srcLayer = f.sourceLayer as string;
      if (!code) return;
      if (
        !hoveredRef.current ||
        hoveredRef.current.id !== code ||
        hoveredRef.current.sourceLayer !== srcLayer
      ) {
        clearHover();
        hoveredRef.current = { source: src, sourceLayer: srcLayer, id: code };
        map.setFeatureState(hoveredRef.current, { hover: true });
      }
      map.getCanvas().style.cursor = "pointer";
      const name = String(f.properties?.nom ?? f.properties?.name ?? code);
      const val = cbRef.current.metricFromFeature?.(f.properties as Record<string, unknown>);
      const label = cbRef.current.metricLabel ? ` · ${cbRef.current.metricLabel}` : "";
      const valTxt = typeof val === "number" ? `<br/>${formatValue(val)}${label}` : "";
      popupRef.current
        ?.setLngLat(e.lngLat)
        .setHTML(`<strong>${escapeHtml(name)}</strong>${valTxt}`)
        .addTo(map);
    });
    map.on("mouseout", clearHover);

    map.on("click", (e) => {
      const feats = map.queryRenderedFeatures(e.point, {
        layers: FILL_LAYERS.filter((l) => map.getLayer(l)),
      });
      if (!feats.length) return;
      const f = feats[0];
      const props = f.properties ?? {};
      const code = String(props.code ?? "");
      if (!code) return;
      const name = props.nom ?? props.name;
      cbRef.current.onFeatureClick?.(code, name != null ? String(name) : undefined);
    });

    return () => {
      window.clearTimeout(kick);
      ro.disconnect();
      map.remove();
      mapRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const src = map.getSource("basemap") as maplibregl.RasterTileSource | undefined;
    if (src) src.setTiles(RASTER_TILES[basemap]);
  }, [basemap]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const apply = () => {
      for (const d of LAYER_DEFS) {
        if (!map.getLayer(d.id)) {
          map.addLayer({
            id: d.id,
            type: "fill",
            source: d.source,
            "source-layer": d.sourceLayer,
            minzoom: d.minzoom,
            maxzoom: d.maxzoom,
            paint: {
              "fill-color": NO_DATA_FILL,
              "fill-opacity": [
                "case",
                ["boolean", ["feature-state", "hover"], false],
                0.9,
                0.62,
              ] as maplibregl.ExpressionSpecification,
            },
          });
          map.addLayer({
            id: `${d.id}-line`,
            type: "line",
            source: d.source,
            "source-layer": d.sourceLayer,
            minzoom: d.minzoom,
            maxzoom: d.maxzoom,
            paint: {
              "line-color": [
                "case",
                ["boolean", ["feature-state", "hover"], false],
                HOVER_LINE,
                "#7c2d12",
              ] as maplibregl.ExpressionSpecification,
              "line-width": [
                "case",
                ["boolean", ["feature-state", "hover"], false],
                2,
                0.5,
              ] as maplibregl.ExpressionSpecification,
              "line-opacity": 0.7,
            },
          });
        }
      }
      recolor(map, metricKey);
    };

    if (map.isStyleLoaded()) apply();
    else map.once("style.load", apply);
  }, [metricKey]);

  return <div ref={containerRef} style={{ width: "100%", height, background: "#abd0f0" }} />;
}

function num(x: unknown): number | null {
  const n = Number(x);
  return Number.isFinite(n) ? n : null;
}

function jsMetricValue(props: Record<string, unknown>, metric: MapMetricKey): number | null {
  switch (metric) {
    case "density": {
      const pop = num(props.population);
      const area = num(props.areaKm2 ?? props.area);
      return pop != null && area != null && area > 0 ? pop / area : null;
    }
    case "gdpPerCapita": {
      const g = num(props.gdpPerCapita);
      if (g != null) return g;
      const gn = num(props.gdpNominal);
      const pop = num(props.population);
      return gn != null && pop != null && pop > 0 ? gn / pop : null;
    }
    case "pollution":
      return num(props.pollutionScore);
    default:
      return num(props[metric]);
  }
}

function recolor(map: maplibregl.Map, metricKey: MapMetricKey) {
  for (const d of LAYER_DEFS) {
    if (!map.getLayer(d.id)) continue;
    const feats = map.queryRenderedFeatures({ layers: [d.id] });
    const values = feats.map((f) =>
      jsMetricValue(f.properties as Record<string, unknown>, metricKey),
    );
    const range = deriveRange(values);
    map.setPaintProperty(
      d.id,
      "fill-color",
      colorExpr(metricKey, range) as maplibregl.ExpressionSpecification,
    );
  }
}

function formatValue(value: number): string {
  if (Math.abs(value) >= 1_000_000) return `${(value / 1_000_000).toFixed(1)} M`;
  if (Math.abs(value) >= 1_000) return `${(value / 1_000).toFixed(0)} k`;
  return value.toLocaleString("fr-FR", { maximumFractionDigits: 1 });
}

function escapeHtml(s: string): string {
  const map: Record<string, string> = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  };
  return s.replace(/[&<>"']/g, (c) => map[c] ?? c);
}
