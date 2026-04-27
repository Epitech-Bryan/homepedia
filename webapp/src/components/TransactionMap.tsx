import { useMemo } from "react";
import { FranceMap } from "@/components/FranceMap";
import { useGeoDepartments, useGeoRegions, useDepartmentStats, useRegionStats } from "@/api/hooks";
import type { TransactionStats } from "@/api/client";

interface TransactionMapProps {
  filters: Record<string, string>;
  stats: TransactionStats | undefined;
}

export function TransactionMap({ filters, stats }: TransactionMapProps) {
  const regionCode = filters.regionCode;
  const departmentCode = filters.departmentCode;

  const showDepartments = !!regionCode;

  const { data: geoRegions } = useGeoRegions();
  const { data: geoDepartments } = useGeoDepartments(regionCode);
  const { data: regionStats } = useRegionStats();
  const { data: departmentStats } = useDepartmentStats(regionCode);

  const geojson = showDepartments ? geoDepartments : geoRegions;
  const activeCode = departmentCode ?? regionCode;

  const metricByCode = useMemo(() => {
    const map: Record<string, number> = {};
    if (showDepartments && departmentStats) {
      for (const d of departmentStats) {
        map[d.code] = d.transactionCount;
      }
    } else if (regionStats) {
      for (const r of regionStats) {
        map[r.code] = r.transactionCount;
      }
    }
    return map;
  }, [showDepartments, departmentStats, regionStats]);

  if (!stats || stats.totalTransactions === 0) return null;

  return (
    <FranceMap
      geojson={geojson ?? null}
      activeFeatureCode={activeCode}
      metricByCode={metricByCode}
      metricLabel="Transactions"
      mapStyle="choropleth"
      height="400px"
    />
  );
}
