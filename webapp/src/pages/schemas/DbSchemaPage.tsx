/**
 * PostgreSQL + MongoDB schema reference. The lists below mirror the live
 * Liquibase changesets (db.changelog-master.yaml) and the @Document /
 * @Indexed annotations on common/src/main/java/.../review/. Update both
 * sides when you add a table / index — there's no auto-sync.
 */

interface TableRow {
  name: string;
  purpose: string;
  cols?: string;
  partitioned?: boolean;
}

interface IndexRow {
  table: string;
  name: string;
  cols: string;
  purpose?: string;
}

const PG_TABLES: TableRow[] = [
  { name: "regions", purpose: "Régions FR (code PK, name, population, area)" },
  { name: "departments", purpose: "Départements FR (FK → regions)" },
  { name: "cities", purpose: "Communes INSEE (FK → departments, lat/lon)" },
  {
    name: "transactions",
    purpose: "Mutations DVF — PK composite (id, mutation_date), fillfactor=100",
    partitioned: true,
  },
  { name: "indicators", purpose: "Indicateurs géo génériques (level, code, category, value)" },
  { name: "geo_boundaries", purpose: "GeoJSON par (level, code)" },
  { name: "dept_dvf_stats", purpose: "Agrégats DVF par département (pré-calculés Spark)" },
  {
    name: "city_dvf_yearly_stats",
    purpose: "Agrégats DVF par (INSEE, année) — alimente /api/stats/*",
  },
  {
    name: "city_price_quarterly_stats",
    purpose: "Agrégats DVF par (INSEE, année, trimestre) — pour timeline €/m² (#9)",
  },
  {
    name: "comparable_transactions",
    purpose: "Top-10 plus proches comparables par transaction (Spark KNN, #11)",
  },
  { name: "admins", purpose: "Comptes admin (username unique)" },
  {
    name: "city_reviews",
    purpose: "Vestigial — la vraie donnée vit dans Mongo (à dropper)",
  },
];

const PG_INDEXES: IndexRow[] = [
  { table: "transactions", name: "idx_transaction_city", cols: "city_insee_code" },
  { table: "transactions", name: "idx_transaction_date", cols: "mutation_date" },
  { table: "transactions", name: "idx_transaction_type", cols: "property_type" },
  {
    table: "transactions",
    name: "idx_transaction_mutation_id",
    cols: "mutation_id",
    purpose: "Dédup multi-lots DVF",
  },
  {
    table: "transactions",
    name: "idx_transaction_mutation_date_brin",
    cols: "BRIN (mutation_date)",
    purpose: "Date-range pruning compact",
  },
  {
    table: "transactions",
    name: "idx_transaction_geocoded_bbox",
    cols: "(latitude, longitude) WHERE lat IS NOT NULL",
    purpose: "Heatmap bbox",
  },
  {
    table: "transactions",
    name: "idx_transaction_geocode_backlog",
    cols: "id WHERE latitude IS NULL",
    purpose: "Backlog géocodage",
  },
  { table: "indicators", name: "idx_indicator_geo", cols: "(level, code)" },
  { table: "indicators", name: "idx_indicator_category", cols: "category" },
  {
    table: "indicators",
    name: "idx_indicator_geo_category",
    cols: "(level, code, category)",
    purpose: "Hot path /api/indicators",
  },
  {
    table: "indicators",
    name: "idx_indicator_iris_code_category",
    cols: "(code, category) WHERE level='IRIS'",
  },
  {
    table: "indicators",
    name: "idx_indicator_iris_code_prefix",
    cols: "code varchar_pattern_ops WHERE level='IRIS'",
    purpose: "LIKE-prefix IRIS",
  },
  {
    table: "cities",
    name: "idx_cities_name_trgm",
    cols: "GIN LOWER(name) gin_trgm_ops",
    purpose: "Autocomplete villes",
  },
  { table: "cities", name: "idx_cities_department_code", cols: "department_code" },
  {
    table: "city_dvf_yearly_stats",
    name: "idx_city_dvf_yearly_stats_year",
    cols: "year",
  },
  {
    table: "city_price_quarterly_stats",
    name: "idx_quarterly_year_q",
    cols: "(year, quarter)",
  },
];

const MONGO_INDEXES = [
  { name: "_id", purpose: "PK implicite" },
  { name: "cityInseeCode", purpose: "@Indexed — lookup principal" },
  { name: "content (text)", purpose: "@TextIndexed — full-text FR, stemming" },
];

export function DbSchemaPage() {
  return (
    <div className="space-y-10 pb-8">
      <section>
        <div className="flex items-baseline justify-between mb-3">
          <h2 className="text-lg font-semibold">PostgreSQL 16</h2>
          <span className="text-xs text-muted-foreground">17 changesets Liquibase</span>
        </div>
        <p className="text-sm text-muted-foreground mb-4">
          Schéma principal. La table <code className="font-mono text-xs">transactions</code> est
          partitionnée par année (2014..2030 + default) avec autovacuum tuné par partition (0.05 /
          0.02).
        </p>

        <h3 className="text-sm font-medium mb-2">Tables</h3>
        <div className="overflow-x-auto rounded-md border mb-6">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Nom</th>
                <th className="px-3 py-2 text-left">Description</th>
              </tr>
            </thead>
            <tbody>
              {PG_TABLES.map((t) => (
                <tr key={t.name} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">
                    {t.name}
                    {t.partitioned && (
                      <span className="ml-2 inline-flex rounded bg-orange-100 text-orange-800 px-1.5 py-0.5 text-[10px] dark:bg-orange-900/40 dark:text-orange-200">
                        partitioned
                      </span>
                    )}
                  </td>
                  <td className="px-3 py-2 text-muted-foreground">{t.purpose}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <h3 className="text-sm font-medium mb-2">Indexes</h3>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Table</th>
                <th className="px-3 py-2 text-left">Nom</th>
                <th className="px-3 py-2 text-left">Colonnes</th>
                <th className="px-3 py-2 text-left">But</th>
              </tr>
            </thead>
            <tbody>
              {PG_INDEXES.map((i) => (
                <tr key={i.name} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{i.table}</td>
                  <td className="px-3 py-2 font-mono text-xs">{i.name}</td>
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{i.cols}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{i.purpose ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">MongoDB 7</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Une seule collection — les reviews avec sentiment. Schéma propre au CityReview entity
          (Spring Data Mongo applique les indexes au boot).
        </p>

        <div className="rounded-md border p-4 mb-4">
          <h3 className="text-sm font-mono mb-2">city_reviews</h3>
          <p className="text-xs text-muted-foreground mb-3">
            Champs : <code>id</code>, <code>cityInseeCode</code>, <code>content</code>,{" "}
            <code>sentimentScore</code>, <code>sentimentLabel</code>, <code>publishedAt</code>,{" "}
            <code>author</code>, <code>rating</code>.
          </p>
          <h4 className="text-xs font-medium mt-2 mb-1 text-muted-foreground uppercase tracking-wide">
            Indexes
          </h4>
          <ul className="text-xs space-y-1">
            {MONGO_INDEXES.map((i) => (
              <li key={i.name}>
                <code className="font-mono">{i.name}</code>
                <span className="ml-2 text-muted-foreground">— {i.purpose}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Redis 7</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Cache <code>@Cacheable</code> avec préfixe versionné. 4 caches, TTL de 15min (reviews) à
          24h (geo).
        </p>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
          {[
            { name: "geo", ttl: "24 h" },
            { name: "refdata", ttl: "12 h" },
            { name: "stats", ttl: "30 min" },
            { name: "reviews", ttl: "15 min" },
          ].map((c) => (
            <div key={c.name} className="rounded-md border bg-card px-3 py-2">
              <div className="font-mono">{c.name}</div>
              <div className="text-muted-foreground mt-1">TTL {c.ttl}</div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
