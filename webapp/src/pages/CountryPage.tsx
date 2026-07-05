import { Link } from "react-router-dom";
import { useCountryStats, useTransactionStats } from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { AreaReviewsSection } from "@/components/AreaReviewsSection";

/**
 * National overview for France — reached by clicking the country on the world
 * map. Shows the country-wide pollution score, national DVF transaction stats
 * and the aggregated resident opinion (sentiment / word cloud / latest reviews)
 * rolled up over every commune.
 */
export function CountryPage() {
  const { data: countryStats } = useCountryStats();
  const { data: stats } = useTransactionStats();

  return (
    <div className="space-y-5">
      <div>
        <p className="text-xs text-muted-foreground">
          <Link to="/" className="hover:underline">
            Monde
          </Link>
          {" / Pays"}
        </p>
        <h1 className="text-xl font-bold tracking-tight mt-1">France</h1>
        <p className="text-muted-foreground text-sm">Vue nationale</p>
      </div>

      {stats && stats.totalTransactions > 0 && (
        <div className="grid grid-cols-2 gap-3">
          <StatCard label="Transactions" value={stats.totalTransactions} />
          <StatCard label="Prix moyen" value={stats.averagePrice} unit="€" />
          <StatCard label="Prix médian" value={stats.medianPrice} unit="€" />
          <StatCard label="Prix €/m²" value={stats.averagePricePerSqm} unit="€/m²" />
        </div>
      )}

      <AreaReviewsSection
        basePath="/country"
        pollutionScore={countryStats?.pollutionScore ?? null}
      />
    </div>
  );
}
