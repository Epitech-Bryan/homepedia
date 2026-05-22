/**
 * High-level system architecture diagram + component list. The boxes are
 * hand-built JSX rather than a Mermaid render so the page stays
 * dependency-free and works offline; the trade-off is that the layout is
 * static and doesn't reflow on small screens — we cap content at max-w-6xl
 * to keep it readable.
 */
export function ArchitectureSchemaPage() {
  return (
    <div className="space-y-8 pb-8">
      <section>
        <h2 className="text-lg font-semibold mb-3">Vue d'ensemble</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Trafic utilisateur → Traefik → SPA React (Vite) ou REST API Spring Boot. Le backend lit
          PostgreSQL (relationnel + DVF partitionné par année), MongoDB (reviews) et Redis (cache
          @Cacheable). Spark tourne offline pour les agrégats lourds.
        </p>

        <div className="font-mono text-[11px] leading-5 border rounded-lg bg-muted/30 p-4 overflow-x-auto">
          <pre className="whitespace-pre">{`            ┌─────────────┐
            │   Browser   │
            └──────┬──────┘
                   │ HTTPS
            ┌──────▼──────┐
            │   Traefik   │  routes: homepedia.localhost / api
            └──┬───────┬──┘
               │       │
        ┌──────▼──┐  ┌─▼──────────────┐
        │  Webapp │  │   REST API     │
        │  Vite   │  │   Spring Boot  │
        │ React 19│  │   Java 21      │
        └─────────┘  └──┬──┬──┬──┬────┘
                       │  │  │  └─────────► Spark master (offline jobs)
                       │  │  │
                  ┌────▼┐ │  └─► ┌──────┐
                  │ PG  │ │      │Mongo │  city_reviews
                  │ 16  │ │      │  7   │  (text + sentiment)
                  └─────┘ │      └──────┘
                          │
                     ┌────▼────┐
                     │  Redis  │  @Cacheable, 4 caches (geo, refdata, stats, reviews)
                     │   7     │
                     └─────────┘`}</pre>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Composants</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <ComponentCard
            title="Webapp (React 19 + Vite)"
            details={[
              "Leaflet + VectorGrid pour la carte choroplèthe",
              "TanStack Query — cache + revalidation des endpoints REST",
              "Tailwind + shadcn/ui pour le design system",
              "Routing : react-router-dom v7",
            ]}
          />
          <ComponentCard
            title="REST API (Spring Boot 3.5 / Java 21)"
            details={[
              "JPA + Hibernate sur PostgreSQL partitionné",
              "Spring Data Mongo pour les reviews",
              "Spring Cache → Redis (Jackson, prefix versionné)",
              "Liquibase, 17 changesets actifs",
              "Resilience4j sur INSEE / ADEME / data.gouv",
            ]}
          />
          <ComponentCard
            title="PostgreSQL 16"
            details={[
              "transactions partitionnée par année (2014..2030)",
              "pg_trgm pour l'autocomplete villes",
              "BRIN sur mutation_date",
              "Pre-agg : city_dvf_yearly_stats, city_price_quarterly_stats",
            ]}
          />
          <ComponentCard
            title="MongoDB 7"
            details={[
              "city_reviews : free text + sentiment + rating",
              "@Indexed sur cityInseeCode",
              "@TextIndexed sur content (French stemming)",
            ]}
          />
          <ComponentCard
            title="Redis 7"
            details={[
              "Préfixe versionné (homepedia:v2:…)",
              "Flush stale au boot (issue #2026-05-21)",
              "Jackson EVERYTHING typing",
            ]}
          />
          <ComponentCard
            title="Spark 3.5"
            details={[
              "CityDvfStatsAggregator → city_dvf_yearly_stats",
              "CityQuarterlyPriceAggregator → city_price_quarterly_stats",
              "ComparableSalesAggregator (KNN, issue #11)",
              "Master + worker via docker compose",
            ]}
          />
        </div>
      </section>
    </div>
  );
}

function ComponentCard({ title, details }: { title: string; details: string[] }) {
  return (
    <div className="rounded-lg border bg-card p-4 shadow-sm">
      <h3 className="font-medium text-sm mb-2">{title}</h3>
      <ul className="text-xs text-muted-foreground space-y-1 list-disc pl-4">
        {details.map((d) => (
          <li key={d}>{d}</li>
        ))}
      </ul>
    </div>
  );
}
