import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { matchPath, useLocation, useNavigate } from "react-router-dom";
import { FranceMap, type MapMarker, type MapStyle } from "@/components/FranceMap";
import {
  useArrondissementsForCities,
  useCitiesForDepartment,
  useCityStats,
  useDepartment,
  useDepartmentStats,
  useGeoCitiesForDepartments,
  useGeoCountries,
  useGeoDepartments,
  useGeoRegions,
  useRegionStats,
} from "@/api/hooks";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { CityStats, DepartmentStats, RegionStats } from "@/api/client";

// Below this zoom, the world view (Natural Earth country boundaries) takes
// over so the user can pan across continents. At/above this we switch to
// French regions and lose the country layer — France is the data-rich
// territory of the app, the rest is browse-only.
const WORLD_ZOOM_THRESHOLD = 5;
// Below this zoom, we show regions; at or above, we switch to departments.
const DEPARTMENT_ZOOM_THRESHOLD = 7;
// Above this zoom, we auto-detect the department under the map center and
// show its cities as sized markers.
const CITY_DETAIL_ZOOM_THRESHOLD = 10;
// Above this zoom, big cities split into their municipal arrondissements.
const ARRONDISSEMENT_ZOOM_THRESHOLD = 12;
// INSEE codes of the only three communes that publish municipal
// arrondissements via geo.api.gouv.fr: Paris, Marseille, Lyon.
const DRILLDOWN_CITY_CODES = new Set(["75056", "13055", "69123"]);

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
  | "averagePrice"
  | "averagePricePerSqm"
  | "transactionCount";

const METRIC_LABELS: Record<MapMetric, string> = {
  population: "Population",
  density: "Density (hab/km²)",
  averagePrice: "Avg. price (€)",
  averagePricePerSqm: "Avg. €/m²",
  transactionCount: "Transactions",
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
    case "averagePrice":
      return s.averagePrice ?? null;
    case "averagePricePerSqm":
      return s.averagePricePerSqm ?? null;
    case "transactionCount":
      return s.transactionCount ?? null;
  }
}

export function PersistentMap() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [metric, setMetric] = useState<MapMetric>("population");
  const [style, setStyle] = useState<MapStyle>("choropleth");
  const [zoom, setZoom] = useState(6);
  const [center, setCenter] = useState<[number, number]>([46.6, 2.5]);
  // Visible map bounds: [south, west, north, east]. Used to fetch commune
  // polygons for every department that intersects the viewport, so cells
  // along the edge don't appear truncated.
  const [bounds, setBounds] = useState<[number, number, number, number]>([41, -5, 51, 10]);
  // Local target for in-map clicks (no URL change). Reset whenever the
  // browser URL changes so deep-links (/regions/X, /departments/X) take over.
  const [clickedFeatureCode, setClickedFeatureCode] = useState<string | undefined>(undefined);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setClickedFeatureCode(undefined);
  }, [pathname]);

  const onCenterChange = useCallback((lat: number, lng: number) => {
    setCenter([lat, lng]);
  }, []);

  // Debounce bounds updates: a single pan emits several `moveend` events as
  // Leaflet eases the inertia, and each one previously triggered the full
  // visibleDeptCodes recompute + possibly N parallel commune-geojson fetches.
  // 200 ms after the user stops moving is short enough to feel instant and
  // enough to coalesce the burst into one update.
  const boundsTimer = useRef<number | null>(null);
  useEffect(
    () => () => {
      if (boundsTimer.current !== null) window.clearTimeout(boundsTimer.current);
    },
    [],
  );
  const onBoundsChange = useCallback((south: number, west: number, north: number, east: number) => {
    if (boundsTimer.current !== null) window.clearTimeout(boundsTimer.current);
    boundsTimer.current = window.setTimeout(() => {
      setBounds([south, west, north, east]);
    }, 200);
  }, []);

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

  // Pre-fetch BOTH layers so zoom-driven switching is instant.
  const { data: geoCountries } = useGeoCountries();
  const { data: geoRegions } = useGeoRegions();
  const { data: geoDepartments } = useGeoDepartments(); // no filter = all 101
  const { data: regionStats } = useRegionStats();
  const { data: allDepartmentStats } = useDepartmentStats(); // all

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
  const visibleDeptCodes = useMemo(() => {
    if (!showCityDetail || !geoDepartments) return [];
    const viewBbox: [number, number, number, number] = [bounds[1], bounds[0], bounds[3], bounds[2]];
    const codes: string[] = [];
    for (const f of geoDepartments.features) {
      const code = (f.properties as { code?: string } | null)?.code;
      if (!code) continue;
      if (bboxesOverlap(featureBbox(f), viewBbox)) {
        codes.push(code);
      }
    }
    return codes.sort();
  }, [showCityDetail, geoDepartments, bounds]);

  const geoCities = useGeoCitiesForDepartments(visibleDeptCodes);

  // At arrondissement zoom, only fetch for the drilldown communes whose bbox
  // intersects the viewport. Outside Paris/Lyon/Marseille this is a no-op.
  const drilldownCityCodes = useMemo(() => {
    if (!showArrondissements || !geoCities) return [];
    const viewBbox: [number, number, number, number] = [bounds[1], bounds[0], bounds[3], bounds[2]];
    const codes: string[] = [];
    for (const f of geoCities.features) {
      const code = (f.properties as { code?: string } | null)?.code;
      if (!code || !DRILLDOWN_CITY_CODES.has(code)) continue;
      if (bboxesOverlap(featureBbox(f), viewBbox)) codes.push(code);
    }
    return codes.sort();
  }, [showArrondissements, geoCities, bounds]);

  const geoArrondissements = useArrondissementsForCities(drilldownCityCodes);

  // City-level geojson: start from communes, then at arrondissement zoom swap
  // out drilldown parent communes for their arrondissement polygons.
  const cityLevelGeojson = useMemo<GeoJSON.FeatureCollection | null>(() => {
    if (!geoCities) return null;
    if (!showArrondissements || !geoArrondissements) return geoCities;
    const drilldownSet = new Set(drilldownCityCodes);
    const filtered = geoCities.features.filter((f) => {
      const code = (f.properties as { code?: string } | null)?.code;
      return !code || !drilldownSet.has(code);
    });
    return {
      type: "FeatureCollection",
      features: [...filtered, ...geoArrondissements.features],
    };
  }, [geoCities, showArrondissements, geoArrondissements, drilldownCityCodes]);

  // 4-tier zoom: countries (world) → regions → departments → city polygons.
  // World view stays France-agnostic; the moment we cross WORLD_ZOOM_THRESHOLD
  // we drop into the data-rich French stack.
  const geojson = showWorld
    ? (geoCountries ?? null)
    : showCityDetail
      ? (cityLevelGeojson ?? geoDepartments ?? null)
      : showDepartments
        ? (geoDepartments ?? null)
        : (geoRegions ?? null);

  // Backend stats for every commune code visible on screen — used to colour
  // the choropleth by averagePrice / €m² / transactionCount at city zoom.
  const cityStatCodes = useMemo(() => {
    if (!showCityDetail || !cityLevelGeojson) return [];
    const codes: string[] = [];
    for (const f of cityLevelGeojson.features) {
      const code = (f.properties as { code?: string } | null)?.code;
      if (code) codes.push(code);
    }
    return codes;
  }, [showCityDetail, cityLevelGeojson]);

  const { data: cityStats } = useCityStats(cityStatCodes);
  const cityStatsByCode = useMemo(() => {
    const map: Record<string, CityStats> = {};
    if (!cityStats) return map;
    for (const s of cityStats) map[s.code] = s;
    return map;
  }, [cityStats]);

  const metricByCode = useMemo(() => {
    const map: Record<string, number | null> = {};
    if (showWorld && geoCountries) {
      // World view: only population is universally available across all 177
      // countries (Natural Earth POP_EST). Density/price/transactions don't
      // map to country granularity. Default to population so the choropleth
      // still tells a story even if the user picked another metric.
      for (const f of geoCountries.features) {
        const props = f.properties as { code?: string; population?: number } | null;
        if (!props?.code) continue;
        map[props.code] = props.population ?? null;
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
      <FranceMap
        geojson={geojson}
        onFeatureClick={onFeatureClick}
        markers={markers}
        onMarkerClick={onMarkerClick}
        activeFeatureCode={activeFeatureCode}
        metricByCode={metricByCode}
        metricLabel={METRIC_LABELS[metric]}
        mapStyle={style}
        height="100%"
        onZoomChange={setZoom}
        onCenterChange={onCenterChange}
        onBoundsChange={onBoundsChange}
        bleed
      />
      <div className="absolute top-3 right-3 z-[1000] flex items-center gap-2 pointer-events-auto">
        <Select value={metric} onValueChange={(v) => setMetric(v as MapMetric)}>
          <SelectTrigger className="w-44 h-8 text-xs bg-background/90 backdrop-blur shadow-sm">
            <SelectValue />
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
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="choropleth">Choropleth</SelectItem>
            <SelectItem value="bubbles">Bubbles</SelectItem>
            <SelectItem value="heat">Heatmap</SelectItem>
            <SelectItem value="all">All</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div className="absolute bottom-3 left-3 z-[1000] pointer-events-auto">
        <span className="inline-flex items-center gap-1.5 rounded-md bg-background/90 backdrop-blur px-2 py-1 text-xs shadow-sm">
          <span className="font-medium uppercase tracking-wide text-muted-foreground">Showing</span>
          <span className="text-foreground">{layerName}</span>
          <span className="text-muted-foreground/60">· zoom {zoom.toFixed(1)}</span>
        </span>
      </div>
    </div>
  );
}
