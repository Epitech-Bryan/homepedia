import { useCallback, useEffect, useMemo, useState } from "react";
import { matchPath, useLocation, useNavigate } from "react-router-dom";
import { FranceMap, type MapMarker, type MapStyle } from "@/components/FranceMap";
import {
  useCitiesForDepartment,
  useDepartment,
  useDepartmentStats,
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
import type { DepartmentStats, RegionStats } from "@/api/client";

const HIDDEN_PATHS = ["/explorer"];

// Below this zoom, we show regions; at or above, we switch to departments.
const DEPARTMENT_ZOOM_THRESHOLD = 7;
// Above this zoom, we auto-detect the department under the map center and
// show its cities as sized markers.
const CITY_DETAIL_ZOOM_THRESHOLD = 9;

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

function extractValue(s: RegionStats | DepartmentStats, metric: MapMetric): number | null {
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
  // Local target for in-map clicks (no URL change). Reset whenever the
  // browser URL changes so deep-links (/regions/X, /departments/X) take over.
  const [clickedFeatureCode, setClickedFeatureCode] = useState<string | undefined>(undefined);

  useEffect(() => {
    setClickedFeatureCode(undefined);
  }, [pathname]);

  const onCenterChange = useCallback((lat: number, lng: number) => {
    setCenter([lat, lng]);
  }, []);

  // URL drives the active feature highlight + cards below the map.
  // The MAP CONTENT (which layer to show) is purely zoom-driven.
  const regionMatch = matchPath("/regions/:code", pathname);
  const departmentMatch = matchPath("/departments/:code", pathname);
  const { data: department } = useDepartment(departmentMatch?.params.code ?? "");
  const activeRegionCode = regionMatch?.params.code ?? department?.regionCode;
  const departmentCode = departmentMatch?.params.code;

  const showDepartments = zoom >= DEPARTMENT_ZOOM_THRESHOLD;
  const showCityDetail = zoom >= CITY_DETAIL_ZOOM_THRESHOLD;

  // Pre-fetch BOTH layers so zoom-driven switching is instant.
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

  const geojson = showDepartments ? (geoDepartments ?? null) : (geoRegions ?? null);

  const metricByCode = useMemo(() => {
    const stats = showDepartments ? (allDepartmentStats ?? []) : (regionStats ?? []);
    const map: Record<string, number | null> = {};
    for (const s of stats) {
      map[s.code] = extractValue(s, metric);
    }
    return map;
  }, [metric, showDepartments, regionStats, allDepartmentStats]);

  // City markers — derived from whichever department code we resolved
  // (auto-detected at high zoom, or URL-selected). FranceMap gates them by
  // a zoom threshold so they don't pollute the choropleth at low zoom.
  const markers: MapMarker[] = useMemo(() => {
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
  }, [cityFetchDeptCode, citiesPage]);

  const onFeatureClick = useCallback((code: string) => {
    // No URL change — just trigger a fly-to via FitBounds. The zoom listener
    // will then auto-switch the layer once we land at the new zoom level.
    setClickedFeatureCode(code);
  }, []);

  const onMarkerClick = useCallback(
    (inseeCode: string) => {
      navigate(`/cities/${inseeCode}`);
    },
    [navigate],
  );

  const isHidden = HIDDEN_PATHS.some((p) => pathname.startsWith(p));
  if (isHidden) return null;

  // Active feature priority: in-map click > URL match > none.
  const urlActive = showDepartments ? departmentCode : (activeRegionCode ?? undefined);
  const activeFeatureCode = clickedFeatureCode ?? urlActive;

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-end gap-3 flex-wrap">
        <label className="text-sm font-medium text-muted-foreground">Color by</label>
        <Select value={metric} onValueChange={(v) => setMetric(v as MapMetric)}>
          <SelectTrigger className="w-56">
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
        <label className="text-sm font-medium text-muted-foreground">Style</label>
        <Select value={style} onValueChange={(v) => setStyle(v as MapStyle)}>
          <SelectTrigger className="w-40">
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
      <FranceMap
        geojson={geojson}
        onFeatureClick={onFeatureClick}
        markers={markers}
        onMarkerClick={onMarkerClick}
        activeFeatureCode={activeFeatureCode}
        metricByCode={metricByCode}
        metricLabel={METRIC_LABELS[metric]}
        mapStyle={style}
        height="450px"
        onZoomChange={setZoom}
        onCenterChange={onCenterChange}
      />
    </div>
  );
}
