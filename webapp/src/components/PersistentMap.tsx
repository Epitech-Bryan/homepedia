import { useCallback, useMemo, useState } from "react";
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

type MapMetric =
  | "none"
  | "population"
  | "density"
  | "averagePrice"
  | "averagePricePerSqm"
  | "transactionCount";

const METRIC_LABELS: Record<MapMetric, string> = {
  none: "Default (uniform)",
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
    default:
      return null;
  }
}

export function PersistentMap() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [metric, setMetric] = useState<MapMetric>("none");
  const [style, setStyle] = useState<MapStyle>("choropleth");

  const regionMatch = matchPath("/regions/:code", pathname);
  const departmentMatch = matchPath("/departments/:code", pathname);

  const departmentCode = departmentMatch?.params.code;
  const { data: department } = useDepartment(departmentCode ?? "");
  const activeRegionCode = regionMatch?.params.code ?? department?.regionCode;

  const { data: geoRegions } = useGeoRegions();
  const { data: geoDepartments } = useGeoDepartments(activeRegionCode);
  const { data: citiesPage } = useCitiesForDepartment(departmentCode);
  const { data: regionStats } = useRegionStats();
  const { data: departmentStats } = useDepartmentStats(activeRegionCode);

  const showDepartments = Boolean(activeRegionCode);
  const geojson = showDepartments ? (geoDepartments ?? null) : (geoRegions ?? null);

  const metricByCode = useMemo(() => {
    if (metric === "none") return undefined;
    const stats = showDepartments ? (departmentStats ?? []) : (regionStats ?? []);
    const map: Record<string, number | null> = {};
    for (const s of stats) {
      map[s.code] = extractValue(s, metric);
    }
    return map;
  }, [metric, showDepartments, regionStats, departmentStats]);

  const markers: MapMarker[] = useMemo(() => {
    if (!departmentCode || !citiesPage?._embedded) return [];
    const cities = Object.values(citiesPage._embedded).flat() as Array<{
      inseeCode: string;
      name: string;
      latitude: number;
      longitude: number;
    }>;
    return cities
      .filter((c) => Number.isFinite(c.latitude) && Number.isFinite(c.longitude))
      .map((c) => ({
        id: c.inseeCode,
        name: c.name,
        lat: c.latitude,
        lon: c.longitude,
      }));
  }, [departmentCode, citiesPage]);

  const onFeatureClick = useCallback(
    (code: string) => {
      if (showDepartments) {
        navigate(`/departments/${code}`);
      } else {
        navigate(`/regions/${code}`);
      }
    },
    [navigate, showDepartments],
  );

  const onMarkerClick = useCallback(
    (inseeCode: string) => {
      navigate(`/cities/${inseeCode}`);
    },
    [navigate],
  );

  const isHidden = HIDDEN_PATHS.some((p) => pathname.startsWith(p));
  if (isHidden) return null;

  const showStyleSelector = metric !== "none";

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
        {showStyleSelector && (
          <>
            <label className="text-sm font-medium text-muted-foreground">Style</label>
            <Select value={style} onValueChange={(v) => setStyle(v as MapStyle)}>
              <SelectTrigger className="w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="choropleth">Choropleth</SelectItem>
                <SelectItem value="bubbles">Bubbles</SelectItem>
                <SelectItem value="both">Both</SelectItem>
              </SelectContent>
            </Select>
          </>
        )}
      </div>
      <FranceMap
        geojson={geojson}
        onFeatureClick={onFeatureClick}
        markers={markers}
        onMarkerClick={onMarkerClick}
        activeFeatureCode={departmentCode}
        metricByCode={metricByCode}
        mapStyle={style}
        height="450px"
      />
    </div>
  );
}
