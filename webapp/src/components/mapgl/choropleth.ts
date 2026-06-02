import maplibregl from "maplibre-gl";

export type MapMetricKey =
  | "population"
  | "density"
  | "gdpPerCapita"
  | "housePriceIndex"
  | "averagePrice"
  | "averagePricePerSqm"
  | "transactionCount"
  | "pollution";

export type Range = { min: number; max: number; breaks?: number[] } | null;

type Expr = unknown[];

export const CHOROPLETH_SCALE = [
  "#fef0d9",
  "#fdd49e",
  "#fdbb84",
  "#fc8d59",
  "#ef6548",
  "#d7301f",
  "#990000",
];
export const NO_DATA_FILL = "#e5e7eb";
export const HOVER_LINE = "#1f2937";

export const LAYER_DEFS = [
  { id: "w-countries-fill", source: "world", sourceLayer: "countries", minzoom: 0, maxzoom: 5 },
  { id: "w-admin1-fill", source: "world", sourceLayer: "admin1", minzoom: 5, maxzoom: 8 },
  { id: "w-admin2-fill", source: "world", sourceLayer: "admin2", minzoom: 8, maxzoom: 11 },
  { id: "w-admin3-fill", source: "world", sourceLayer: "admin3", minzoom: 11, maxzoom: 13 },
  { id: "c-regions-fill", source: "cities", sourceLayer: "regions", minzoom: 5, maxzoom: 7 },
  {
    id: "c-departments-fill",
    source: "cities",
    sourceLayer: "departments",
    minzoom: 7,
    maxzoom: 9,
  },
  { id: "c-cities-fill", source: "cities", sourceLayer: "cities", minzoom: 9, maxzoom: 22 },
] as const;

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

export function deriveRange(values: Array<number | null | undefined>): Range {
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

// A choropleth range available synchronously from props — the backend
// quantile range for the France layers, else one derived from the country
// metricByCode the parent already holds. Lets the fill expression be set the
// moment a layer is created, so features colour as their tiles paint instead
// of flashing grey until the first idle-driven recolor.
export function propRange(
  choroplethRange: Range | undefined,
  metricByCode: Record<string, number | null | undefined> | undefined,
): Range {
  if (choroplethRange && choroplethRange.max > choroplethRange.min) return choroplethRange;
  return deriveRange(Object.values(metricByCode ?? {}));
}

export function colorExpr(metric: MapMetricKey, range: Range): Expr {
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

export function recolor(map: maplibregl.Map, metricKey: MapMetricKey, fallback: Range): void {
  const z = map.getZoom();
  for (const d of LAYER_DEFS) {
    if (!map.getLayer(d.id)) continue;
    if (z < d.minzoom || z >= d.maxzoom) continue;
    const feats = map.queryRenderedFeatures({ layers: [d.id] });
    const values = feats.map((f) =>
      jsMetricValue(f.properties as Record<string, unknown>, metricKey),
    );
    // Adaptive per-zoom range when tiles are already rendered; otherwise the
    // prop-derived range so the layer never sits grey waiting for tiles.
    const range = deriveRange(values) ?? fallback;
    map.setPaintProperty(
      d.id,
      "fill-color",
      colorExpr(metricKey, range) as maplibregl.ExpressionSpecification,
    );
  }
}

function collectCoords(geom: GeoJSON.Geometry, out: number[][]): void {
  const walk = (c: unknown): void => {
    if (Array.isArray(c) && typeof c[0] === "number") {
      out.push(c as number[]);
    } else if (Array.isArray(c)) {
      for (const child of c) walk(child);
    }
  };
  if ("coordinates" in geom) walk((geom as { coordinates: unknown }).coordinates);
}

export function recomputeBubbles(map: maplibregl.Map, metricKey: MapMetricKey): void {
  const src = map.getSource("bubbles") as maplibregl.GeoJSONSource | undefined;
  if (!src) return;
  const layers = LAYER_DEFS.map((d) => d.id).filter((id) => map.getLayer(id));
  const feats = layers.length ? map.queryRenderedFeatures({ layers }) : [];
  const agg = new Map<string, { sx: number; sy: number; n: number; value: number; name: string }>();
  for (const f of feats) {
    const props = f.properties ?? {};
    const code = String(props.code ?? "");
    if (!code) continue;
    const value = jsMetricValue(props as Record<string, unknown>, metricKey);
    if (value == null || value <= 0) continue;
    const coords: number[][] = [];
    collectCoords(f.geometry, coords);
    if (!coords.length) continue;
    let sx = 0;
    let sy = 0;
    for (const [x, y] of coords) {
      sx += x;
      sy += y;
    }
    const e = agg.get(code);
    if (e) {
      e.sx += sx;
      e.sy += sy;
      e.n += coords.length;
    } else {
      agg.set(code, {
        sx,
        sy,
        n: coords.length,
        value,
        name: String(props.nom ?? props.name ?? code),
      });
    }
  }
  const range = deriveRange([...agg.values()].map((e) => e.value));
  const features: GeoJSON.Feature[] = [];
  for (const [code, e] of agg) {
    const ratio = range
      ? Math.max(0, Math.min(1, (e.value - range.min) / (range.max - range.min)))
      : 0.5;
    features.push({
      type: "Feature",
      geometry: { type: "Point", coordinates: [e.sx / e.n, e.sy / e.n] },
      properties: { code, name: e.name, value: e.value, r: 6 + ratio * 22 },
    });
  }
  src.setData({ type: "FeatureCollection", features });
}
