import { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import { matchPath, useLocation, useNavigate } from "react-router-dom";
import { FranceMap, type MapMarker, type MapStyle } from "@/components/FranceMap";
import FranceMapGL from "@/components/FranceMapGL";
import {
  useArrondissementsForCities,
  useCitiesForDepartment,
  useCityStats,
  useDepartment,
  useDepartmentStats,
  useGeoBelgiumMunicipalities,
  useGeoBelgiumProvinces,
  useGeoCitiesForDepartments,
  useGeoCountries,
  useGeoDepartments,
  useGeoRegions,
  useGeoWorldAdmin1,
  useGeoWorldAdmin2,
  useRegionStats,
  useTileMetricRanges,
  useTransactionHeatPoints,
  useTransactionMarkers,
} from "@/api/hooks";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { CityStats, DepartmentStats, RegionStats } from "@/api/client";

// Below this zoom, the world view: countries are the foreground (each
// country including France appears as one shape, choropleth driven by
// the Natural Earth POP_EST). At/above, the data-rich France stack
// (regions → departments → cities) takes over and countries become a
// grey backdrop.
const WORLD_ZOOM_THRESHOLD = 5;
// Below this zoom, we show regions; at or above, we switch to departments.
const DEPARTMENT_ZOOM_THRESHOLD = 7;
// Above this zoom, we auto-detect the department under the map center and
// show its cities. Aligned with the tippecanoe cities layer (z9-14) so the
// switch from departments to cities is a clean hand-off, no overlap.
const CITY_DETAIL_ZOOM_THRESHOLD = 9;
// Above this zoom, big cities split into their municipal arrondissements.
const ARRONDISSEMENT_ZOOM_THRESHOLD = 12;
// INSEE codes of the only three communes that publish municipal
// arrondissements via geo.api.gouv.fr: Paris, Marseille, Lyon.
const DRILLDOWN_CITY_CODES = new Set(["75056", "13055", "69123"]);

// worldCopyJump lets the camera glide across the antimeridian, but the
// foreground GeoJSON is only painted once. The result is a "ghost" world
// without country borders next to the real one. Duplicating the country
// FeatureCollection at -360°/+360° fills both adjacent copies so borders
// are continuous regardless of how far the user pans.
function shiftGeometry(geom: GeoJSON.Geometry, dx: number): GeoJSON.Geometry {
  const shiftRing = (ring: number[][]): number[][] =>
    ring.map(([x, y, ...rest]) => [x + dx, y, ...rest]);
  switch (geom.type) {
    case "Polygon":
      return { ...geom, coordinates: geom.coordinates.map(shiftRing) };
    case "MultiPolygon":
      return { ...geom, coordinates: geom.coordinates.map((poly) => poly.map(shiftRing)) };
    default:
      return geom;
  }
}

/**
 * Natural Earth ships countries that span the antimeridian (Russia, Fiji,
 * Kiribati, USA via Alaska) as a MultiPolygon where the eastern fragments
 * carry negative longitudes (e.g. Russia's Chukotka at -180..-170). Drawn
 * as-is on the central world copy, those fragments render thousands of km
 * away from their parent — Russia looks chopped in two at the map edges.
 *
 * Detect features whose polygons straddle the dateline and shift the
 * far-negative rings by +360 so the geometry is contiguous in the central
 * world. The subsequent ±360 wrap then renders that contiguous body on the
 * two adjacent world copies as expected.
 */
function stitchAntimeridian(geom: GeoJSON.Geometry): GeoJSON.Geometry {
  if (geom.type !== "MultiPolygon") return geom;
  const shiftRing = (ring: number[][]): number[][] =>
    ring.map(([x, y, ...rest]) => [x + 360, y, ...rest]);
  // Aggregate per-polygon longitude min/max to detect the split.
  const polyBboxes = geom.coordinates.map((poly) => {
    let minX = Infinity;
    let maxX = -Infinity;
    for (const ring of poly)
      for (const [x] of ring) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
      }
    return [minX, maxX] as const;
  });
  const hasEastern = polyBboxes.some(([, mx]) => mx >= 100);
  const hasFarWestern = polyBboxes.some(([mn, mx]) => mn >= -180 && mx <= -100);
  if (!hasEastern || !hasFarWestern) return geom;
  return {
    ...geom,
    coordinates: geom.coordinates.map((poly, i) =>
      polyBboxes[i][1] <= -100 ? poly.map(shiftRing) : poly,
    ),
  };
}

function wrapAcrossDateline(
  fc: GeoJSON.FeatureCollection | null | undefined,
): GeoJSON.FeatureCollection | null {
  if (!fc) return null;
  const stitched: GeoJSON.Feature[] = fc.features.map((f) => ({
    ...f,
    geometry: stitchAntimeridian(f.geometry),
  }));
  const shifted = (offset: number): GeoJSON.Feature[] =>
    stitched.map((f) => ({ ...f, geometry: shiftGeometry(f.geometry, offset) }));
  return {
    type: "FeatureCollection",
    features: [...shifted(-360), ...stitched, ...shifted(360)],
  };
}

function pointInRing(lng: number, lat: number, ring: number[][]): boolean {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i];
    const [xj, yj] = ring[j];
    const intersect = yi > lat !== yj > lat && lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi;
    if (intersect) inside = !inside;
  }
  return inside;
}

function findFeatureCodeAt(
  geojson: GeoJSON.FeatureCollection | undefined | null,
  lng: number,
  lat: number,
): string | null {
  if (!geojson) return null;
  for (const f of geojson.features) {
    const code = (f.properties as { code?: string } | null)?.code;
    if (!code) continue;
    const geo = f.geometry;
    if (geo.type === "Polygon" && pointInRing(lng, lat, geo.coordinates[0])) {
      return code;
    }
    if (geo.type === "MultiPolygon") {
      for (const poly of geo.coordinates) {
        if (pointInRing(lng, lat, poly[0])) return code;
      }
    }
  }
  return null;
}

// Cache the bbox per Feature so a pan doesn't re-walk every polygon's
// coordinate tree. Department polygons can hold thousands of points each,
// and the previous version ran the recursion on all 101 features at every
// move event. WeakMap garbage-collects the entries when the geojson
// reference itself goes out of scope.
const FEATURE_BBOX_CACHE = new WeakMap<GeoJSON.Feature, [number, number, number, number]>();

function featureBbox(feature: GeoJSON.Feature): [number, number, number, number] {
  const cached = FEATURE_BBOX_CACHE.get(feature);
  if (cached) return cached;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  const visit = (coords: unknown): void => {
    if (Array.isArray(coords) && typeof coords[0] === "number") {
      const [x, y] = coords as [number, number];
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;
    } else if (Array.isArray(coords)) {
      for (const c of coords) visit(c);
    }
  };
  visit((feature.geometry as { coordinates: unknown }).coordinates);
  const bbox: [number, number, number, number] = [minX, minY, maxX, maxY];
  FEATURE_BBOX_CACHE.set(feature, bbox);
  return bbox;
}

function bboxesOverlap(
  a: [number, number, number, number],
  b: [number, number, number, number],
): boolean {
  return a[0] <= b[2] && a[2] >= b[0] && a[1] <= b[3] && a[3] >= b[1];
}

type MapMetric =
  | "population"
  | "density"
  | "gdpPerCapita"
  | "averagePrice"
  | "averagePricePerSqm"
  | "transactionCount"
  | "pollution";

const METRIC_LABELS: Record<MapMetric, string> = {
  population: "Population",
  density: "Density (hab/km²)",
  gdpPerCapita: "GDP per capita (USD)",
  averagePrice: "Avg. price (€)",
  averagePricePerSqm: "Avg. €/m²",
  transactionCount: "Transactions",
  // 1 = cleanest (mostly GES-A buildings), 7 = most polluting (GES-G).
  // Sourced from the ADEME DPE feed; only the commune layer has it baked in.
  pollution: "Pollution (GES 1-7)",
};

// Human label per MapStyle. The Select.Value render function reads from
// here so the trigger shows "Choropleth" instead of the raw value "choropleth".
const MAP_STYLE_LABELS: Record<string, string> = {
  choropleth: "Choropleth",
  bubbles: "Bubbles",
  heat: "Heatmap",
  all: "All",
};

type MapBasemap = "voyager" | "satellite";
const MAP_BASEMAP_LABELS: Record<MapBasemap, string> = {
  voyager: "Plan",
  satellite: "Satellite",
};

function extractValue(
  s: RegionStats | DepartmentStats | CityStats,
  metric: MapMetric,
): number | null {
  switch (metric) {
    case "population":
      return s.population ?? null;
    case "density":
      return s.population && s.area ? s.population / s.area : null;
    case "gdpPerCapita":
      // Backend stats don't carry GDP. Only meaningful at world zoom.
      return null;
    case "averagePrice":
      return s.averagePrice ?? null;
    case "averagePricePerSqm":
      return s.averagePricePerSqm ?? null;
    case "transactionCount":
      return s.transactionCount ?? null;
    case "pollution":
      // Only CityStats carries pollutionScore today (commune-level GES from
      // ADEME). Region/department stats don't aggregate it yet → null falls
      // through to the gray "no data" fill on the choropleth.
      return "pollutionScore" in s ? ((s as CityStats).pollutionScore ?? null) : null;
  }
}

export function PersistentMap() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [metric, setMetric] = useState<MapMetric>("population");
  const [style, setStyle] = useState<MapStyle>("choropleth");
  const [basemap, setBasemap] = useState<MapBasemap>("voyager");
  const [view, setView] = useState<"2d" | "globe">(
    import.meta.env.VITE_USE_MAPLIBRE === "true" ? "globe" : "2d",
  );
  // Initial state matches FranceMap's INITIAL_CENTER/INITIAL_ZOOM: world
  // view with no upfront bias toward any country. The 4-tier zoom logic
  // takes over as soon as the user zooms in past 5.
  const [zoom, setZoom] = useState(2);
  const [center, setCenter] = useState<[number, number]>([20, 10]);
  // Visible map bounds: [south, west, north, east]. World-level bounds at
  // boot, the map view-tracker updates them as the user pans/zooms.
  const [bounds, setBounds] = useState<[number, number, number, number]>([-60, -180, 75, 180]);
  // Local target for in-map clicks (no URL change). Reset whenever the
  // browser URL changes so deep-links (/regions/X, /departments/X) take over.
  const [clickedFeatureCode, setClickedFeatureCode] = useState<string | undefined>(undefined);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setClickedFeatureCode(undefined);
  }, [pathname]);

  // Debounce center updates: Leaflet emits `move` at the screen refresh rate
  // during a drag, and the only consumer of `center` is `autoDeptCode` —
  // a point-in-polygon scan over the 101 departments. Running that scan at
  // 60 Hz freezes the main thread mid-pan. 80 ms coalesces a drag into 2-3
  // updates max, the PIP stays cheap, and `autoDeptCode → cityFetchDeptCode
  // → useCitiesForDepartment` only refetches once the user actually lets go.
  const centerTimer = useRef<number | null>(null);
  useEffect(
    () => () => {
      if (centerTimer.current !== null) window.clearTimeout(centerTimer.current);
    },
    [],
  );
  const onCenterChange = useCallback((lat: number, lng: number) => {
    if (centerTimer.current !== null) window.clearTimeout(centerTimer.current);
    centerTimer.current = window.setTimeout(() => {
      setCenter([lat, lng]);
    }, 80);
  }, []);

  // Bounds updates feed straight into state — React 19's useDeferredValue
  // below handles the prioritisation. Pan emits a burst of `moveend` events
  // (Leaflet's inertia easing); React batches the state writes inside the
  // current frame, then schedules dependent work (visibleDeptCodes /
  // drilldownCityCodes / metric range recompute) at a lower priority so the
  // camera animation stays at 60 fps even on a 4× CPU slowdown. Old manual
  // 50 ms debounce removed — it was leaving the first pan visibly stuttery.
  const onBoundsChange = useCallback((south: number, west: number, north: number, east: number) => {
    setBounds([south, west, north, east]);
  }, []);
  // Deferred view: the high-cost useMemos (bbox sweeps over 101 depts,
  // arrondissement filter, MVT redraws keyed on range/min/max) read these
  // instead of the live state. While the user is actively dragging, the
  // deferred copy lags by one frame and React keeps the gesture responsive.
  const deferredBounds = useDeferredValue(bounds);

  // URL drives the active feature highlight + cards below the map.
  // The MAP CONTENT (which layer to show) is purely zoom-driven.
  const regionMatch = matchPath("/regions/:code", pathname);
  const departmentMatch = matchPath("/departments/:code", pathname);
  const { data: department } = useDepartment(departmentMatch?.params.code ?? "");
  const activeRegionCode = regionMatch?.params.code ?? department?.regionCode;
  const departmentCode = departmentMatch?.params.code;

  const showWorld = zoom < WORLD_ZOOM_THRESHOLD;
  const showDepartments = zoom >= DEPARTMENT_ZOOM_THRESHOLD;
  const showCityDetail = zoom >= CITY_DETAIL_ZOOM_THRESHOLD;
  const showArrondissements = zoom >= ARRONDISSEMENT_ZOOM_THRESHOLD;
  // Vector tiles take over the commune layer at city zoom. The MVT pipeline
  // ships polygons with stats baked in, so the SVG path is pure overhead
  // (and at city zoom it would otherwise blast ~45 k polygons through one
  // shared canvas in overlayPane, painting the entire département with the
  // choropleth fill and visually covering the underlying basemap).
  //
  // Default ON — flip to "false" to fall back to SVG when running without
  // the mbtiles file (tests, CI, or a stripped dev image).
  const useVectorTiles = showCityDetail && import.meta.env.VITE_USE_VECTOR_TILES !== "false";
  const useWorldVectorTiles = import.meta.env.VITE_USE_WORLD_TILES !== "false";

  // Pre-fetch every layer so zoom-driven switching is instant.
  const { data: geoCountries } = useGeoCountries();
  const { data: geoBelgiumProvinces } = useGeoBelgiumProvinces();
  // Lazy-load world admin1 only when the user actually leaves the world
  // view. ~1.5 MB on the wire — no point paying for it on a Paris-only
  // session.
  const { data: geoWorldAdmin1 } = useGeoWorldAdmin1();
  const { data: geoRegions } = useGeoRegions();
  const { data: geoDepartments } = useGeoDepartments(); // no filter = all 101
  // Admin-2 covers the level below admin-1 (DEU Kreise, GBR districts,
  // ITA province etc.) — only fetch past department zoom, where the
  // detail becomes readable. ~3.7 MB on the wire so we avoid paying for
  // it on world / region browse.
  const { data: geoWorldAdmin2 } = useGeoWorldAdmin2(showDepartments);
  const { data: regionStats } = useRegionStats();
  const { data: allDepartmentStats } = useDepartmentStats(); // all

  // Stitch French regions + non-FR admin-1 (Belgian provinces today, more
  // countries later) into a single foreground FeatureCollection at zoom
  // 5..7. They never overlap geographically, so a single LeafletGeoJSON
  // can render them all and the metricByCode lookup keys naturally on
  // each feature's `code`.
  const regionsFeatureCollection = useMemo<GeoJSON.FeatureCollection | null>(() => {
    const features: GeoJSON.Feature[] = [];
    if (geoRegions) features.push(...geoRegions.features);
    if (geoBelgiumProvinces) features.push(...geoBelgiumProvinces.features);
    if (geoWorldAdmin1) features.push(...geoWorldAdmin1.features);
    if (features.length === 0) return null;
    return { type: "FeatureCollection", features };
  }, [geoRegions, geoBelgiumProvinces, geoWorldAdmin1]);

  // At high zoom, auto-detect the department under the map center via PIP and
  // load its cities. Falls back to the URL-selected department if any.
  const autoDeptCode = useMemo(() => {
    if (!showCityDetail) return undefined;
    return findFeatureCodeAt(geoDepartments, center[1], center[0]) ?? undefined;
  }, [showCityDetail, geoDepartments, center]);

  const cityFetchDeptCode = autoDeptCode ?? departmentCode;
  const { data: citiesPage } = useCitiesForDepartment(cityFetchDeptCode);

  // At city zoom, load commune polygons for every department whose bbox
  // intersects the viewport — neighbours included so the borders look natural.
  // Skipped entirely when vector tiles are on: the MVT layer already ships
  // those polygons + their stats, paying for the geo.api.gouv.fr fetch on
  // every pan is pure overhead.
  const visibleDeptCodes = useMemo(() => {
    if (!showCityDetail || useVectorTiles || !geoDepartments) return [];
    const viewBbox: [number, number, number, number] = [
      deferredBounds[1],
      deferredBounds[0],
      deferredBounds[3],
      deferredBounds[2],
    ];
    const codes: string[] = [];
    for (const f of geoDepartments.features) {
      const code = (f.properties as { code?: string } | null)?.code;
      if (!code) continue;
      if (bboxesOverlap(featureBbox(f), viewBbox)) {
        codes.push(code);
      }
    }
    return codes.sort();
  }, [showCityDetail, useVectorTiles, geoDepartments, deferredBounds]);

  const geoCities = useGeoCitiesForDepartments(visibleDeptCodes);

  // At arrondissement zoom, only fetch for the drilldown communes whose bbox
  // intersects the viewport. Outside Paris/Lyon/Marseille this is a no-op.
  // Skipped when vector tiles are on (arrondissements are baked into the MVT
  // cities layer, no SVG overlay needed).
  const drilldownCityCodes = useMemo(() => {
    if (!showArrondissements || useVectorTiles || !geoCities) return [];
    const viewBbox: [number, number, number, number] = [
      deferredBounds[1],
      deferredBounds[0],
      deferredBounds[3],
      deferredBounds[2],
    ];
    const codes: string[] = [];
    for (const f of geoCities.features) {
      const code = (f.properties as { code?: string } | null)?.code;
      if (!code || !DRILLDOWN_CITY_CODES.has(code)) continue;
      if (bboxesOverlap(featureBbox(f), viewBbox)) codes.push(code);
    }
    return codes.sort();
  }, [showArrondissements, useVectorTiles, geoCities, deferredBounds]);

  const geoArrondissements = useArrondissementsForCities(drilldownCityCodes);

  // Belgian communes — 581 GADM polygons with Wikidata-sourced population.
  // Fetched only at city zoom when vector tiles are off; when on, the MVT
  // pipeline ships its own commune layer and the BE overlay would just
  // double-draw the borders.
  const { data: geoBelgiumMunicipalities } = useGeoBelgiumMunicipalities(
    showCityDetail && !useVectorTiles,
  );

  // City-level geojson: start from communes (FR + BE) + world admin-2 so
  // German Kreise, Italian province, UK districts etc. stay visible at
  // city zoom instead of vanishing into a bare basemap. At arrondissement
  // zoom we swap drilldown parent communes for their arrondissement
  // polygons. Returns null early under vector tiles — every downstream
  // consumer (`geojson` final, `cityStatCodes`, `metricByCode` city
  // branch) already short-circuits in that case.
  const cityLevelGeojson = useMemo<GeoJSON.FeatureCollection | null>(() => {
    if (useVectorTiles) return null;
    if (!geoCities && !geoWorldAdmin2) return null;
    const baseFeatures: GeoJSON.Feature[] = [];
    if (geoCities) baseFeatures.push(...geoCities.features);
    if (geoBelgiumMunicipalities) baseFeatures.push(...geoBelgiumMunicipalities.features);
    if (geoWorldAdmin2) baseFeatures.push(...geoWorldAdmin2.features);
    if (!showArrondissements || !geoArrondissements)
      return { type: "FeatureCollection", features: baseFeatures };
    const drilldownSet = new Set(drilldownCityCodes);
    const filtered = baseFeatures.filter((f) => {
      const code = (f.properties as { code?: string } | null)?.code;
      return !code || !drilldownSet.has(code);
    });
    return {
      type: "FeatureCollection",
      features: [...filtered, ...geoArrondissements.features],
    };
  }, [
    useVectorTiles,
    geoCities,
    geoBelgiumMunicipalities,
    geoWorldAdmin2,
    showArrondissements,
    geoArrondissements,
    drilldownCityCodes,
  ]);

  // The countries layer is the only one where wrap-around matters: it's
  // the foreground at world zoom AND the grey backdrop at every higher
  // zoom. Wrap once and reuse so the WeakMap-cached bbox lookups don't
  // fight a fresh reference on every render.
  const wrappedCountries = useMemo(() => wrapAcrossDateline(geoCountries), [geoCountries]);

  // Countries that already have a higher-resolution overlay (FR regions +
  // BE provinces from geo.api.gouv.fr / Statbel, plus the ~38 EU + G20
  // states/provinces in the world-admin1 dataset). When these layers are
  // active the Natural Earth country borders bleed through underneath
  // and create visible "ghost" outlines next to the precise ones; filter
  // them out of the backdrop so the precise overlay is the only thing
  // drawing those borders.
  const preciselyOverlaidCountries = useMemo(() => {
    const set = new Set<string>(["FRA", "BEL"]);
    if (geoWorldAdmin1) {
      for (const f of geoWorldAdmin1.features) {
        const c = (f.properties as { country?: string } | null)?.country;
        if (c) set.add(c);
      }
    }
    return set;
  }, [geoWorldAdmin1]);

  const backdropCountries = useMemo<GeoJSON.FeatureCollection | null>(() => {
    if (!wrappedCountries) return null;
    if (preciselyOverlaidCountries.size === 0) return wrappedCountries;
    return {
      type: "FeatureCollection",
      features: wrappedCountries.features.filter((f) => {
        const code = (f.properties as { code?: string } | null)?.code;
        return !code || !preciselyOverlaidCountries.has(code);
      }),
    };
  }, [wrappedCountries, preciselyOverlaidCountries]);

  // FR departements + world admin-2 stitched into one collection at
  // department zoom: they're geographically disjoint (FR has départements,
  // DE has Kreise, etc.) so a single LeafletGeoJSON renders them all and
  // the choropleth styleFor closure reads each feature's properties
  // uniformly. World admin-2 only loads past showDepartments so a
  // France-only session never pays for it.
  const departmentsFeatureCollection = useMemo<GeoJSON.FeatureCollection | null>(() => {
    if (!geoDepartments) return geoWorldAdmin2 ?? null;
    if (!geoWorldAdmin2) return geoDepartments;
    return {
      type: "FeatureCollection",
      features: [...geoDepartments.features, ...geoWorldAdmin2.features],
    };
  }, [geoDepartments, geoWorldAdmin2]);

  // 4-tier zoom: world (countries) → regions+BE provinces+world admin-1
  // → departments+world admin-2 → city/arrondissement. At world zoom
  // France is just one country shape among the others — the user is
  // dezoomed past the point where regional detail would even be
  // readable. As soon as we cross the threshold we drop into the
  // data-rich stack with countries kept as a grey backdrop. When vector
  // tiles take over the commune layer we pass {@code null} for the
  // geojson prop so FranceMap doesn't double-draw with its
  // LeafletGeoJSON polygons.
  const geojson = showWorld
    ? (wrappedCountries ?? null)
    : showCityDetail
      ? useVectorTiles
        ? null
        : (cityLevelGeojson ?? departmentsFeatureCollection ?? null)
      : showDepartments
        ? (departmentsFeatureCollection ?? null)
        : (regionsFeatureCollection ?? null);

  // Backend stats for every commune code visible on screen — used to colour
  // the choropleth by averagePrice / €m² / transactionCount at city zoom.
  // With vector tiles on, the per-commune values come baked into the MVT
  // features so we skip this request entirely; the global metric range
  // (legend, ratio computation) is served from /tiles/cities/metric-ranges.
  const cityStatCodes = useMemo(() => {
    if (!showCityDetail || useVectorTiles || !cityLevelGeojson) return [];
    const codes: string[] = [];
    for (const f of cityLevelGeojson.features) {
      const code = (f.properties as { code?: string } | null)?.code;
      // GADM codes ("DEU.1.1_1" etc.) carry "." and aren't known to the
      // /stats/cities endpoint — keep this list FR/BE-shaped only.
      if (code && !code.includes(".")) codes.push(code);
    }
    return codes;
  }, [showCityDetail, useVectorTiles, cityLevelGeojson]);

  const { data: cityStats } = useCityStats(cityStatCodes);
  const { data: tileMetricRanges } = useTileMetricRanges();
  const cityStatsByCode = useMemo(() => {
    const map: Record<string, CityStats> = {};
    if (!cityStats) return map;
    for (const s of cityStats) map[s.code] = s;
    return map;
  }, [cityStats]);

  // Extract the active metric directly from an MVT feature's properties.
  // `CityTileBuilder` bakes `population`, `areaKm2`, `transactionCount`,
  // `averagePrice` and `averagePricePerSqm` into each commune feature, so
  // panning at city zoom no longer fans out a {@code /stats/cities} call
  // per visible viewport. Returns null when the metric isn't applicable
  // (e.g. `gdpPerCapita` at city level) so the layer falls back to
  // `metricByCode`.
  const metricFromFeature = useMemo(() => {
    return (props: Record<string, unknown>): number | null => {
      switch (metric) {
        case "population": {
          const v = props.population;
          return typeof v === "number" ? v : null;
        }
        case "density": {
          const pop = props.population;
          const areaKm2 = props.areaKm2 ?? props.area;
          // `surface` (hectares) is the geo.api.gouv.fr fallback shipped
          // in legacy tiles; convert to km² so the choropleth ratio stays
          // consistent across baked / unbaked features.
          const areaFromHectares = props.surface;
          const km2 =
            typeof areaKm2 === "number"
              ? areaKm2
              : typeof areaFromHectares === "number"
                ? areaFromHectares / 100
                : null;
          return typeof pop === "number" && km2 && km2 > 0 ? pop / km2 : null;
        }
        case "averagePrice": {
          const v = props.averagePrice;
          return typeof v === "number" ? v : null;
        }
        case "averagePricePerSqm": {
          const v = props.averagePricePerSqm;
          return typeof v === "number" ? v : null;
        }
        case "transactionCount": {
          const v = props.transactionCount;
          return typeof v === "number" ? v : null;
        }
        case "gdpPerCapita": {
          // Baked by CountryGeoService for the world admin-1 layer; coverage
          // is sparse (~30% of regions) and the field is missing on
          // commune-level features, both of which the null-guard handles
          // naturally — null falls through to the gray "no data" fill.
          const v = props.gdpPerCapita;
          if (typeof v === "number") return v;
          const gdp = props.gdpNominal;
          const pop = props.population;
          if (typeof gdp === "number" && typeof pop === "number" && pop > 0) {
            return gdp / pop;
          }
          return null;
        }
        case "pollution": {
          // Baked into the commune MVT layer by CityTileBuilder
          // (weighted GES class). Higher = more polluting.
          const v = props.pollutionScore;
          return typeof v === "number" ? v : null;
        }
      }
    };
  }, [metric]);

  // Precomputed min/max for the current metric across every commune. Lets
  // the choropleth legend size itself without the parent having to read
  // every visible polygon's metric value first — important when the per-
  // commune values live in tile features the parent can't introspect.
  const tileChoroplethRange = useMemo(() => {
    if (!useVectorTiles) return null;
    const r = tileMetricRanges?.[metric];
    if (!r || r.min == null || r.max == null) return null;
    if (r.min === r.max) return null;
    return { min: r.min, max: r.max, breaks: r.breaks ?? [] };
  }, [useVectorTiles, tileMetricRanges, metric]);

  const metricByCode = useMemo(() => {
    const map: Record<string, number | null> = {};
    if (showWorld && geoCountries) {
      // Country-level: population, density (computed from spherical area
      // baked in by the backend) and GDP per capita (Natural Earth GDP_MD
      // / population) are universally available. The DVF housing metrics
      // are FR-only so they leave the choropleth empty at world zoom.
      for (const f of geoCountries.features) {
        const props = f.properties as {
          code?: string;
          population?: number;
          area?: number;
          gdp?: number;
        } | null;
        if (!props?.code) continue;
        const pop = props.population ?? null;
        const areaKm2 = props.area ?? null;
        const gdpMd = props.gdp ?? null;
        switch (metric) {
          case "population":
            map[props.code] = pop;
            break;
          case "density":
            map[props.code] = pop && areaKm2 ? pop / areaKm2 : null;
            break;
          case "gdpPerCapita":
            // GDP_MD is in millions USD — multiply back to USD before
            // dividing by population.
            map[props.code] = gdpMd && pop ? (gdpMd * 1_000_000) / pop : null;
            break;
          default:
            map[props.code] = null;
        }
      }
      return map;
    }
    if (showCityDetail && cityLevelGeojson) {
      // City-level: prefer backend aggregate stats (price metrics, real
      // population/area) — fall back to geo.api.gouv.fr properties for
      // population/density when the backend has no row (e.g. arrondissements,
      // not yet ingested).
      for (const f of cityLevelGeojson.features) {
        const props = f.properties as {
          code?: string;
          population?: number;
          surface?: number;
        } | null;
        if (!props?.code) continue;
        const backend = cityStatsByCode[props.code];
        if (backend) {
          map[props.code] = extractValue(backend, metric);
          continue;
        }
        // Geo-API fallback. `surface` is in hectares — divide by 100 for km².
        const pop = props.population ?? null;
        const areaKm2 = props.surface ? props.surface / 100 : null;
        let value: number | null;
        switch (metric) {
          case "population":
            value = pop;
            break;
          case "density":
            value = pop && areaKm2 ? pop / areaKm2 : null;
            break;
          default:
            value = null;
        }
        map[props.code] = value;
      }
      return map;
    }
    const stats = showDepartments ? (allDepartmentStats ?? []) : (regionStats ?? []);
    for (const s of stats) {
      map[s.code] = extractValue(s, metric);
    }
    // Pull non-FR admin-1 (Belgian provinces + world admin-1) values
    // straight from the geojson properties — population + area + gdpNominal
    // are baked in there by CountryGeoService. Density derives client-side
    // from pop/area; gdpPerCapita from gdp/pop. Housing market metrics
    // stay empty (FR-only data so far).
    if (!showDepartments) {
      const collections = [geoBelgiumProvinces, geoWorldAdmin1].filter(
        (c): c is GeoJSON.FeatureCollection => !!c,
      );
      for (const fc of collections) {
        for (const f of fc.features) {
          const props = f.properties as {
            code?: string;
            population?: number;
            area?: number;
            gdpNominal?: number;
            gdpPerCapita?: number;
          } | null;
          if (!props?.code) continue;
          const pop = props.population ?? null;
          const area = props.area ?? null;
          const gdp = props.gdpNominal ?? null;
          switch (metric) {
            case "population":
              map[props.code] = pop;
              break;
            case "density":
              map[props.code] = pop && area ? pop / area : null;
              break;
            case "gdpPerCapita":
              map[props.code] = props.gdpPerCapita ?? (gdp && pop && pop > 0 ? gdp / pop : null);
              break;
            default:
              map[props.code] = null;
          }
        }
      }
    }
    return map;
  }, [
    metric,
    showWorld,
    geoCountries,
    showCityDetail,
    showDepartments,
    cityLevelGeojson,
    cityStatsByCode,
    regionStats,
    allDepartmentStats,
    geoBelgiumProvinces,
    geoWorldAdmin1,
  ]);

  // City markers are only useful when we DON'T have commune polygons yet;
  // once polygons load they tell the same story more cleanly. So we drop
  // markers as soon as we're at city zoom.
  const markers: MapMarker[] = useMemo(() => {
    if (showCityDetail) return [];
    if (!cityFetchDeptCode || !citiesPage?._embedded) return [];
    const cities = Object.values(citiesPage._embedded).flat() as Array<{
      inseeCode: string;
      name: string;
      latitude: number;
      longitude: number;
      population: number | null;
    }>;
    return cities
      .filter((c) => Number.isFinite(c.latitude) && Number.isFinite(c.longitude))
      .map((c) => ({
        id: c.inseeCode,
        name: c.name,
        lat: c.latitude,
        lon: c.longitude,
        value: c.population ?? undefined,
      }));
  }, [showCityDetail, cityFetchDeptCode, citiesPage]);

  const onFeatureClick = useCallback(
    (code: string) => {
      // GADM admin codes always contain a "." (e.g. "DEU.2_1" admin-1,
      // "DEU.2.5_1" admin-2) whereas FR INSEE codes are pure digits. Route
      // world admin-1 clicks to the dedicated detail page; admin-2 has no
      // page yet so we just highlight (no-op nav).
      if (code.includes(".")) {
        const dots = (code.match(/\./g) ?? []).length;
        if (dots === 1) {
          navigate(`/world/admin1/${encodeURIComponent(code)}`);
        } else {
          setClickedFeatureCode(code);
        }
        return;
      }
      // At city zoom, polygons are commune outlines — clicking them goes to
      // the city detail page (same as marker click). Otherwise just zoom.
      if (showCityDetail) {
        navigate(`/cities/${code}`);
      } else {
        setClickedFeatureCode(code);
      }
    },
    [showCityDetail, navigate],
  );

  const onMarkerClick = useCallback(
    (inseeCode: string) => {
      navigate(`/cities/${inseeCode}`);
    },
    [navigate],
  );

  const urlActive = showDepartments ? departmentCode : (activeRegionCode ?? undefined);
  const activeFeatureCode = clickedFeatureCode ?? urlActive;

  // Per-address heatmap layer. Only fetched when the user is at city zoom
  // (the bbox is small enough to be useful) and has picked a metric the
  // backend can aggregate from geocoded transactions. Other metrics fall
  // back to the polygon-shape sampling inside FranceMap.
  const heatBackendMetric: "averagePrice" | "averagePricePerSqm" | "transactionCount" | null =
    metric === "averagePrice" || metric === "averagePricePerSqm" || metric === "transactionCount"
      ? metric
      : null;
  const heatEnabled =
    showCityDetail && (style === "heat" || style === "all") && heatBackendMetric != null;
  const { data: precisionHeatPoints } = useTransactionHeatPoints({
    south: deferredBounds[0],
    west: deferredBounds[1],
    north: deferredBounds[2],
    east: deferredBounds[3],
    metric: heatBackendMetric ?? "averagePricePerSqm",
    enabled: heatEnabled,
  });

  // Per-transaction pins, only past the arrondissement zoom — beyond that
  // point the viewport is small enough that the backend will actually
  // return rows (it rejects bboxes > 0.2°) and the markers add value over
  // the heatmap alone.
  const markersEnabled = zoom >= ARRONDISSEMENT_ZOOM_THRESHOLD;
  const { data: transactionMarkers } = useTransactionMarkers({
    south: deferredBounds[0],
    west: deferredBounds[1],
    north: deferredBounds[2],
    east: deferredBounds[3],
    enabled: markersEnabled,
  });

  const hasArrondissements = showArrondissements && (geoArrondissements?.features.length ?? 0) > 0;
  const layerName = hasArrondissements
    ? "Arrondissements"
    : showCityDetail
      ? "Cities"
      : showDepartments
        ? "Departments"
        : "Regions";

  return (
    <div className="relative h-full w-full">
      {view === "globe" ? (
        <FranceMapGL
          metricKey={metric}
          metricByCode={metricByCode}
          metricFromFeature={metricFromFeature}
          choroplethRange={tileChoroplethRange}
          metricLabel={METRIC_LABELS[metric]}
          onFeatureClick={onFeatureClick}
          activeFeatureCode={activeFeatureCode}
          basemap={basemap}
          mapStyle={style}
          precisionHeatPoints={heatEnabled ? precisionHeatPoints : undefined}
          transactionMarkers={markersEnabled ? transactionMarkers : undefined}
          height="100%"
          onZoomChange={setZoom}
          onCenterChange={onCenterChange}
          onBoundsChange={onBoundsChange}
        />
      ) : (
        <FranceMap
          geojson={geojson}
          // Country outlines as a backdrop at every zoom EXCEPT world view
          // (where countries are already the foreground — rendering them
          // twice would just double-draw). Keeps the user oriented when
          // zoomed on a French region or commune.
          baseGeojson={!showWorld ? backdropCountries : null}
          onFeatureClick={onFeatureClick}
          markers={markers}
          onMarkerClick={onMarkerClick}
          activeFeatureCode={activeFeatureCode}
          metricByCode={metricByCode}
          metricFromFeature={metricFromFeature}
          choroplethRange={tileChoroplethRange}
          metricLabel={METRIC_LABELS[metric]}
          precisionHeatPoints={heatEnabled ? precisionHeatPoints : undefined}
          transactionMarkers={markersEnabled ? transactionMarkers : undefined}
          useVectorTilesForCities={useVectorTiles}
          useVectorTilesForWorld={useWorldVectorTiles}
          basemap={basemap}
          mapStyle={style}
          height="100%"
          onZoomChange={setZoom}
          onCenterChange={onCenterChange}
          onBoundsChange={onBoundsChange}
          bleed
        />
      )}
      <div className="absolute top-3 right-3 z-[1000] flex items-center gap-2 pointer-events-auto">
        <button
          type="button"
          onClick={() => setView((v) => (v === "globe" ? "2d" : "globe"))}
          className="h-8 px-3 rounded-md text-xs font-medium bg-background/90 backdrop-blur shadow-sm border hover:bg-background"
          title={view === "globe" ? "Repasser en carte 2D" : "Voir la Terre en 3D (globe)"}
        >
          {view === "globe" ? "🗺️ 2D" : "🌍 Globe"}
        </button>
        <Select value={metric} onValueChange={(v) => setMetric(v as MapMetric)}>
          <SelectTrigger className="w-44 h-8 text-xs bg-background/90 backdrop-blur shadow-sm">
            <SelectValue>{(v: string) => METRIC_LABELS[v as MapMetric] ?? v}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            {Object.entries(METRIC_LABELS).map(([key, label]) => (
              <SelectItem key={key} value={key}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={style} onValueChange={(v) => setStyle(v as MapStyle)}>
          <SelectTrigger className="w-32 h-8 text-xs bg-background/90 backdrop-blur shadow-sm">
            <SelectValue>{(v: string) => MAP_STYLE_LABELS[v] ?? v}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            {Object.entries(MAP_STYLE_LABELS).map(([key, label]) => (
              <SelectItem key={key} value={key}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={basemap} onValueChange={(v) => setBasemap(v as MapBasemap)}>
          <SelectTrigger className="w-28 h-8 text-xs bg-background/90 backdrop-blur shadow-sm">
            <SelectValue>{(v: string) => MAP_BASEMAP_LABELS[v as MapBasemap] ?? v}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            {Object.entries(MAP_BASEMAP_LABELS).map(([key, label]) => (
              <SelectItem key={key} value={key}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="absolute top-3 left-3 z-[1000] pointer-events-auto">
        <span className="inline-flex items-center gap-1.5 rounded-md bg-background/90 backdrop-blur px-2 py-1 text-xs shadow-sm">
          <span className="font-medium uppercase tracking-wide text-muted-foreground">Showing</span>
          <span className="text-foreground">{layerName}</span>
          <span className="text-muted-foreground/60">· zoom {zoom.toFixed(1)}</span>
        </span>
      </div>
    </div>
  );
}
