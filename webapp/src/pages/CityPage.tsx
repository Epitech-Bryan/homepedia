import { useParams, Link } from "react-router-dom";
import { useCity, useTransactionStats } from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { PriceChart } from "@/components/PriceChart";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Button } from "@/components/ui/button";

export function CityPage() {
  const { code = "" } = useParams<{ code: string }>();
  const { data: city, isLoading, error } = useCity(code);
  const { data: stats } = useTransactionStats(code ? { cityInseeCode: code } : undefined);

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error.message} />;
  if (!city) return <ErrorMessage message="City not found" />;

  const chartData =
    stats && stats.totalTransactions > 0
      ? [
          { label: "Average", value: stats.averagePrice },
          { label: "Median", value: stats.medianPrice },
          { label: "Min", value: stats.minPrice },
          { label: "Max", value: stats.maxPrice },
        ]
      : [];

  return (
    <div className="space-y-5">
      <div>
        <p className="text-xs text-muted-foreground">
          <Link to="/" className="hover:underline">
            France
          </Link>
          {" / "}
          <Link to={`/departments/${city.departmentCode}`} className="hover:underline">
            Dept. {city.departmentCode}
          </Link>
          {" / City"}
        </p>
        <h1 className="text-xl font-bold tracking-tight mt-1">{city.name}</h1>
        <p className="text-muted-foreground text-sm">
          {city.postalCode} · INSEE {city.inseeCode}
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <StatCard label="Population" value={city.population ?? 0} />
        <StatCard label="Area" value={city.area ?? 0} unit="km²" />
        {stats && stats.totalTransactions > 0 && (
          <>
            <StatCard label="Transactions" value={stats.totalTransactions} />
            <StatCard label="Avg. €/m²" value={stats.averagePricePerSqm} unit="€/m²" />
          </>
        )}
      </div>

      {stats && stats.totalTransactions > 0 && (
        <div className="grid grid-cols-2 gap-3">
          <StatCard label="Avg. Price" value={stats.averagePrice} unit="€" />
          <StatCard label="Median" value={stats.medianPrice} unit="€" />
        </div>
      )}

      {chartData.length > 0 && <PriceChart data={chartData} title="Price Overview" />}

      <Link to={`/cities/${code}/reviews`}>
        <Button variant="outline" className="w-full">
          View Reviews &amp; Opinions
        </Button>
      </Link>
    </div>
  );
}
