import { useCallback, useMemo } from "react";
import { matchPath, useLocation, useNavigate } from "react-router-dom";
import { FranceMap, type MapMarker } from "@/components/FranceMap";
import {
  useCitiesForDepartment,
  useDepartment,
  useGeoDepartments,
  useGeoRegions,
} from "@/api/hooks";

const HIDDEN_PATHS = ["/explorer"];

export function PersistentMap() {
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const regionMatch = matchPath("/regions/:code", pathname);
  const departmentMatch = matchPath("/departments/:code", pathname);

  // Resolve which region's departments to show.
  // - On /regions/:code → that region's code directly
  // - On /departments/:code → fetch the department to get its parent region
  const departmentCode = departmentMatch?.params.code;
  const { data: department } = useDepartment(departmentCode ?? "");
  const activeRegionCode = regionMatch?.params.code ?? department?.regionCode;

  const { data: geoRegions } = useGeoRegions();
  const { data: geoDepartments } = useGeoDepartments(activeRegionCode);
  const { data: citiesPage } = useCitiesForDepartment(departmentCode);

  const showDepartments = Boolean(activeRegionCode);
  const geojson = showDepartments ? (geoDepartments ?? null) : (geoRegions ?? null);

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

  // Hide the map on routes where it doesn't make sense
  const isHidden = HIDDEN_PATHS.some((p) => pathname.startsWith(p));
  if (isHidden) return null;

  return (
    <FranceMap
      geojson={geojson}
      onFeatureClick={onFeatureClick}
      markers={markers}
      onMarkerClick={onMarkerClick}
      activeFeatureCode={departmentCode}
      height="450px"
    />
  );
}
