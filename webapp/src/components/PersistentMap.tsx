import { useCallback } from "react";
import { matchPath, useLocation, useNavigate } from "react-router-dom";
import { FranceMap } from "@/components/FranceMap";
import { useDepartment, useGeoDepartments, useGeoRegions } from "@/api/hooks";

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

  const showDepartments = Boolean(activeRegionCode);
  const geojson = showDepartments ? (geoDepartments ?? null) : (geoRegions ?? null);

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

  // Hide the map on routes where it doesn't make sense
  const isHidden =
    HIDDEN_PATHS.some((p) => pathname.startsWith(p)) || pathname.startsWith("/cities/");
  if (isHidden) return null;

  return <FranceMap geojson={geojson} onFeatureClick={onFeatureClick} height="450px" />;
}
