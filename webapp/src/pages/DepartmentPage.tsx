import { useParams, Link, useNavigate } from "react-router-dom";
import { useDepartment, useCities, useTransactionStats } from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Badge } from "@/components/ui/badge";
import type { CitySummary } from "@/api/client";

export function DepartmentPage() {
  const { code = "" } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const { data: dept, isLoading, error } = useDepartment(code);
  const { data: citiesPage } = useCities(code ? { departmentCode: code } : undefined);
  const { data: stats } = useTransactionStats(code ? { departmentCode: code } : undefined);

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error.message} />;
  if (!dept) return <ErrorMessage message="Department not found" />;

  const cities: CitySummary[] = citiesPage?._embedded
    ? (Object.values(citiesPage._embedded).flat() as CitySummary[])
    : [];

  return (
    <div className="space-y-5">
      <div>
        <p className="text-xs text-muted-foreground">
          <Link to="/" className="hover:underline">
            France
          </Link>
          {" / "}
          <Link to={`/regions/${dept.regionCode}`} className="hover:underline">
            Region {dept.regionCode}
          </Link>
          {" / Dept."}
        </p>
        <h1 className="text-xl font-bold tracking-tight mt-1">{dept.name}</h1>
        <p className="text-muted-foreground text-sm">{dept.code}</p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <StatCard label="Population" value={dept.population ?? 0} />
        <StatCard label="Area" value={dept.area ?? 0} unit="km²" />
        <StatCard label="Cities" value={cities.length} />
        {stats && stats.averagePrice > 0 && (
          <StatCard label="Avg. Price" value={stats.averagePrice} unit="€" />
        )}
      </div>

      {stats && stats.totalTransactions > 0 && (
        <div className="grid grid-cols-2 gap-3">
          <StatCard label="Transactions" value={stats.totalTransactions} />
          <StatCard label="Avg. €/m²" value={stats.averagePricePerSqm ?? 0} unit="€/m²" />
        </div>
      )}

      <div>
        <h2 className="text-sm font-semibold mb-2">
          Cities ({citiesPage?.page?.totalElements ?? cities.length})
        </h2>
        <div className="space-y-1.5 max-h-[50vh] overflow-y-auto">
          {cities.map((city) => (
            <button
              key={city.inseeCode}
              type="button"
              onClick={() => navigate(`/cities/${city.inseeCode}`)}
              className="flex w-full items-center justify-between rounded-md border px-3 py-2 text-sm hover:bg-accent transition-colors text-left"
            >
              <span className="font-medium">{city.name}</span>
              <Badge variant="outline" className="text-xs">
                {city.postalCode}
              </Badge>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
