import { Link, useParams } from "react-router-dom";
import { useWorldAdmin1Detail } from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

function formatPop(n: number | null | undefined): string {
  if (n == null) return "—";
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)} M hab.`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(0)} k hab.`;
  return `${Math.round(n)} hab.`;
}

function formatArea(n: number | null | undefined): string {
  if (n == null) return "—";
  return `${Math.round(n).toLocaleString("fr-FR")} km²`;
}

function formatDensity(pop: number | null, area: number | null): string {
  if (!pop || !area) return "—";
  return `${Math.round(pop / area).toLocaleString("fr-FR")} hab/km²`;
}

function formatUsd(n: number | null | undefined): string {
  if (n == null) return "—";
  if (n >= 1_000_000_000_000) return `${(n / 1_000_000_000_000).toFixed(2)} T $`;
  if (n >= 1_000_000_000) return `${(n / 1_000_000_000).toFixed(1)} Md $`;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(0)} M $`;
  return `${Math.round(n).toLocaleString("fr-FR")} $`;
}

/**
 * Page for a single world admin-1 region (Bavaria, Texas, Tokyo
 * prefecture, etc.). Reached by clicking the polygon on the world
 * MVT layer. Counterpart of {@code RegionPage} for FR regions, but
 * the data we have for non-FR is metric-only (no DVF/DPE), so the
 * layout is a single "Key figures" panel + a link back to the parent
 * country and a hint about the sub-regions.
 */
export function WorldRegionPage() {
  const { code = "" } = useParams<{ code: string }>();
  const { data, isLoading, error } = useWorldAdmin1Detail(code);

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error.message} />;
  if (!data) return <ErrorMessage message="Région inconnue" />;

  return (
    <div className="space-y-5">
      <div>
        <p className="text-xs text-muted-foreground">
          <Link to="/" className="hover:underline">
            Monde
          </Link>
          {data.country && (
            <>
              {" / "}
              <span className="font-mono">{data.country}</span>
            </>
          )}
          {" / "}
          {data.name ?? data.code}
        </p>
        <h1 className="mt-1 text-xl font-bold tracking-tight">{data.name ?? data.code}</h1>
        <p className="text-xs text-muted-foreground font-mono">{data.code}</p>
      </div>

      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm">Chiffres clés</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-3">
            <StatCard label="Population" value={formatPop(data.population)} />
            <StatCard label="Superficie" value={formatArea(data.area)} />
            <StatCard label="Densité" value={formatDensity(data.population, data.area)} />
            <StatCard label="PIB nominal" value={formatUsd(data.gdpNominal)} />
            <StatCard label="PIB / habitant" value={formatUsd(data.gdpPerCapita)} />
            <StatCard label="Subdivisions admin-2" value={data.admin2Count} />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm">Sources des données</CardTitle>
        </CardHeader>
        <CardContent className="text-xs text-muted-foreground space-y-1">
          <p>
            Polygone : <span className="font-mono">GADM 4.1</span> · simplifié à ~4 km
          </p>
          <p>
            Métriques : <span className="font-mono">Wikidata</span> (P1082 pop, P2046 area, P2131
            GDP) — coverage variable selon la région.
          </p>
          {data.admin2Count > 0 && (
            <p>
              Cette région contient <strong>{data.admin2Count}</strong> subdivision(s) admin-2.
              Zoome sur la carte (z=8+) pour les voir.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
