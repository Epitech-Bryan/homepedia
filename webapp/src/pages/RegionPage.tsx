import { useParams, useNavigate, Link } from "react-router-dom";
import { useRegion, useDepartments, useTransactionStats } from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Badge } from "@/components/ui/badge";
import type { DepartmentSummary } from "@/api/client";

export function RegionPage() {
  const { code = "" } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const { data: region, isLoading, error } = useRegion(code);
  const { data: deptPage } = useDepartments(code ? { regionCode: code } : undefined);
  const { data: stats } = useTransactionStats(code ? { regionCode: code } : undefined);

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error.message} />;
  if (!region) return <ErrorMessage message="Region not found" />;

  const departments: DepartmentSummary[] = deptPage?._embedded
    ? (Object.values(deptPage._embedded).flat() as DepartmentSummary[])
    : [];

  return (
    <div className="space-y-5">
      <div>
        <p className="text-xs text-muted-foreground">
          <Link to="/" className="hover:underline">
            France
          </Link>{" "}
          / Region
        </p>
        <h1 className="text-xl font-bold tracking-tight mt-1">{region.name}</h1>
        <p className="text-muted-foreground text-sm">{region.code}</p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <StatCard label="Population" value={region.population ?? 0} />
        <StatCard label="Area" value={region.area ?? 0} unit="km²" />
        <StatCard label="Departments" value={departments.length} />
        {stats && stats.averagePrice > 0 && (
          <StatCard label="Avg. Price" value={stats.averagePrice} unit="€" />
        )}
      </div>

      <div>
        <h2 className="text-sm font-semibold mb-2">Departments ({departments.length})</h2>
        <div className="space-y-1.5">
          {departments.map((dept) => (
            <button
              key={dept.code}
              type="button"
              onClick={() => navigate(`/departments/${dept.code}`)}
              className="flex w-full items-center justify-between rounded-md border px-3 py-2 text-sm hover:bg-accent transition-colors text-left"
            >
              <span className="font-medium">{dept.name}</span>
              <Badge variant="outline" className="text-xs">
                {dept.code}
              </Badge>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
