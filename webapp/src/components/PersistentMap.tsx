import { useCallback, useEffect, useMemo, useState } from "react";
import { matchPath, useLocation, useNavigate } from "react-router-dom";
import { FranceMap, type MapMarker, type MapStyle } from "@/components/FranceMap";
import {
  useCitiesForDepartment,
  useDepartment,
  useDepartmentStats,
  useGeoCitiesForDepartments,
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
import { Button } from "@/components/ui/button";
import { Maximize2, Minimize2 } from "lucide-react";
import type { DepartmentStats, RegionStats } from "@/api/client";

const HIDDEN_PATHS = ["/explorer"];

// Below this zoom, we show regions; at or above, we switch to departments.
const DEPARTMENT_ZOOM_THRESHOLD = 7;
// Above this zoom, we auto-detect the department under the map center and
// show its cities as sized markers.
const CITY_DETAIL_ZOOM_THRESHOLD = 10;

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

function featureBbox(feature: GeoJSON.Feature): [number, number, number, number] {
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
  return [minX, minY, maxX, maxY];
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
  const [expanded, setExpanded] = useState(false);
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

  const onBoundsChange = useCallback((south: number, west: number, north: number, east: number) => {
    setBounds([south, west, north, east]);
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

  // 3-tier zoom: regions → departments → city polygons.
  const geojson = showCityDetail
    ? (geoCities ?? geoDepartments ?? null)
    : showDepartments
      ? (geoDepartments ?? null)
      : (geoRegions ?? null);

  const metricByCode = useMemo(() => {
    const map: Record<string, number | null> = {};
    if (showCityDetail && geoCities) {
      // City-level: derive metric from commune geojson properties (population /
      // surface in hectares from geo.api.gouv.fr).
      for (const f of geoCities.features) {
        const props = f.properties as {
          code?: string;
          population?: number;
          surface?: number;
        } | null;
        if (!props?.code) continue;
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
            // Other metrics not available at city level (no per-city stats yet)
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
  }, [metric, showCityDetail, showDepartments, geoCities, regionStats, allDepartmentStats]);

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

  const isHidden = HIDDEN_PATHS.some((p) => pathname.startsWith(p));
  if (isHidden) return null;

  // Active feature priority: in-map click > URL match > none.
  const urlActive = showDepartments ? departmentCode : (activeRegionCode ?? undefined);
  const activeFeatureCode = clickedFeatureCode ?? urlActive;

  const layerName = showCityDetail ? "Cities" : showDepartments ? "Departments" : "Regions";

  return (
    <div className="space-y-3">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center justify-between gap-3 flex-wrap">
        <div className="text-xs text-muted-foreground">
          <span className="font-medium uppercase tracking-wide">Showing</span>{" "}
          <span className="text-foreground">{layerName}</span>{" "}
          <span className="text-muted-foreground/60">· zoom {zoom.toFixed(1)}</span>
        </div>
        <div className="flex items-center gap-3 flex-wrap">
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
          <Button
            variant="outline"
            size="sm"
            onClick={() => setExpanded((v) => !v)}
            aria-label={expanded ? "Shrink map" : "Expand map"}
          >
            {expanded ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
          </Button>
        </div>
      </div>
      <div className={expanded ? "" : "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8"}>
        <FranceMap
          geojson={geojson}
          onFeatureClick={onFeatureClick}
          markers={markers}
          onMarkerClick={onMarkerClick}
          activeFeatureCode={activeFeatureCode}
          metricByCode={metricByCode}
          metricLabel={METRIC_LABELS[metric]}
          mapStyle={style}
          height={expanded ? "78vh" : "500px"}
          onZoomChange={setZoom}
          onCenterChange={onCenterChange}
          onBoundsChange={onBoundsChange}
          bleed={expanded}
        />
      </div>
    </div>
  );
}
