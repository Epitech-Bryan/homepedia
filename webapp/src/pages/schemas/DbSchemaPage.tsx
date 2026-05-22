import { ReactFlow, Background, Controls, MiniMap, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { memo } from "react";
import { Handle, Position, type NodeProps } from "@xyflow/react";

/**
 * ER-style diagram for the Postgres + Mongo schema. Each table renders as a
 * mini-card listing its columns; arrows materialize foreign keys. Same
 * Liquibase + JPA pairing as the live backend — keep this page in sync when
 * you add a new changeset.
 */

interface TableNodeData {
  name: string;
  partitioned?: boolean;
  cols: { name: string; type: string; tag?: "PK" | "FK" | "IX" }[];
  note?: string;
  [key: string]: unknown;
}

const TableNode = memo(function TableNode({ data }: NodeProps) {
  const d = data as unknown as TableNodeData;
  return (
    <div className="min-w-[220px] rounded-md border border-border bg-card shadow-sm">
      <div className="flex items-center justify-between border-b bg-muted/40 px-3 py-1.5">
        <span className="font-mono text-xs font-semibold">{d.name}</span>
        {d.partitioned && (
          <span className="ml-2 inline-flex rounded bg-orange-100 text-orange-800 px-1.5 py-0.5 text-[10px] dark:bg-orange-900/40 dark:text-orange-200">
            partitioned
          </span>
        )}
      </div>
      <ul className="px-3 py-2 text-[11px] leading-relaxed font-mono">
        {d.cols.map((c) => (
          <li key={c.name} className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-1.5">
              {c.tag === "PK" && <span className="text-amber-600 dark:text-amber-400">●</span>}
              {c.tag === "FK" && <span className="text-sky-600 dark:text-sky-400">○</span>}
              {c.tag === "IX" && <span className="text-violet-600 dark:text-violet-400">◇</span>}
              <span>{c.name}</span>
            </span>
            <span className="text-muted-foreground">{c.type}</span>
          </li>
        ))}
      </ul>
      {d.note && (
        <div className="border-t px-3 py-1.5 text-[10px] text-muted-foreground">{d.note}</div>
      )}
      <Handle
        type="target"
        position={Position.Top}
        className="!h-1.5 !w-1.5 !bg-muted-foreground"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!h-1.5 !w-1.5 !bg-muted-foreground"
      />
      <Handle
        type="target"
        position={Position.Left}
        id="left"
        className="!h-1.5 !w-1.5 !bg-muted-foreground"
      />
      <Handle
        type="source"
        position={Position.Right}
        id="right"
        className="!h-1.5 !w-1.5 !bg-muted-foreground"
      />
    </div>
  );
});

const nodeTypes = { table: TableNode };

const NODES: Node[] = [
  {
    id: "regions",
    type: "table",
    position: { x: 20, y: 20 },
    data: {
      name: "regions",
      cols: [
        { name: "code", type: "varchar(3)", tag: "PK" },
        { name: "name", type: "varchar(255)" },
        { name: "population", type: "bigint" },
        { name: "area", type: "double" },
      ],
    },
  },
  {
    id: "departments",
    type: "table",
    position: { x: 320, y: 20 },
    data: {
      name: "departments",
      cols: [
        { name: "code", type: "varchar(3)", tag: "PK" },
        { name: "name", type: "varchar(255)" },
        { name: "region_code", type: "varchar(3)", tag: "FK" },
        { name: "population", type: "bigint" },
        { name: "area", type: "double" },
      ],
    },
  },
  {
    id: "cities",
    type: "table",
    position: { x: 620, y: 20 },
    data: {
      name: "cities",
      cols: [
        { name: "insee_code", type: "varchar(5)", tag: "PK" },
        { name: "name", type: "varchar(255)" },
        { name: "department_code", type: "varchar(3)", tag: "FK" },
        { name: "population", type: "bigint" },
        { name: "area", type: "double" },
        { name: "latitude", type: "double" },
        { name: "longitude", type: "double" },
        { name: "name (trgm)", type: "GIN", tag: "IX" },
      ],
    },
  },

  {
    id: "transactions",
    type: "table",
    position: { x: 900, y: 20 },
    data: {
      name: "transactions",
      partitioned: true,
      cols: [
        { name: "id", type: "bigint" },
        { name: "mutation_date", type: "date", tag: "PK" },
        { name: "mutation_id", type: "varchar", tag: "IX" },
        { name: "city_insee_code", type: "varchar(5)", tag: "FK" },
        { name: "property_type", type: "varchar" },
        { name: "property_value", type: "numeric" },
        { name: "built_surface", type: "double" },
        { name: "latitude", type: "double" },
        { name: "longitude", type: "double" },
      ],
      note: "Partition par année · BRIN sur mutation_date",
    },
  },

  {
    id: "city_dvf_yearly_stats",
    type: "table",
    position: { x: 620, y: 360 },
    data: {
      name: "city_dvf_yearly_stats",
      cols: [
        { name: "insee_code", type: "varchar(5)", tag: "PK" },
        { name: "year", type: "int", tag: "PK" },
        { name: "transaction_count", type: "bigint" },
        { name: "total_price", type: "numeric" },
        { name: "total_residential_surface", type: "double" },
        { name: "updated_at", type: "timestamp" },
      ],
      note: "Pre-agg DVF · alimente CityTileBuilder",
    },
  },
  {
    id: "city_price_quarterly",
    type: "table",
    position: { x: 900, y: 360 },
    data: {
      name: "city_price_quarterly_stats",
      cols: [
        { name: "insee_code", type: "varchar(5)", tag: "PK" },
        { name: "year", type: "int", tag: "PK" },
        { name: "quarter", type: "int", tag: "PK" },
        { name: "avg_price_per_sqm", type: "double" },
        { name: "transaction_count", type: "bigint" },
      ],
      note: "Timeline €/m² (issue #9)",
    },
  },

  {
    id: "dept_dvf_stats",
    type: "table",
    position: { x: 20, y: 360 },
    data: {
      name: "dept_dvf_stats",
      cols: [
        { name: "department_code", type: "varchar(3)", tag: "PK" },
        { name: "transaction_count", type: "bigint" },
        { name: "avg_price", type: "numeric" },
        { name: "avg_price_per_sqm", type: "double" },
        { name: "median_price", type: "numeric" },
      ],
      note: "Pré-calcul Spark (perf #11)",
    },
  },

  {
    id: "indicators",
    type: "table",
    position: { x: 320, y: 360 },
    data: {
      name: "indicators",
      cols: [
        { name: "id", type: "bigserial", tag: "PK" },
        { name: "level", type: "varchar", tag: "IX" },
        { name: "code", type: "varchar", tag: "IX" },
        { name: "category", type: "varchar" },
        { name: "indicator", type: "varchar" },
        { name: "value", type: "double" },
      ],
      note: "INSEE Filosofi / IRIS",
    },
  },

  {
    id: "comparable_transactions",
    type: "table",
    position: { x: 1200, y: 20 },
    data: {
      name: "comparable_transactions",
      cols: [
        { name: "transaction_id", type: "bigint", tag: "FK" },
        { name: "comparable_id", type: "bigint", tag: "FK" },
        { name: "rank", type: "int" },
        { name: "distance_m", type: "double" },
        { name: "price_per_sqm_ratio", type: "double" },
      ],
      note: "KNN Spark (issue #11)",
    },
  },

  {
    id: "geo_boundaries",
    type: "table",
    position: { x: 1200, y: 360 },
    data: {
      name: "geo_boundaries",
      cols: [
        { name: "level", type: "varchar", tag: "PK" },
        { name: "code", type: "varchar", tag: "PK" },
        { name: "geojson", type: "jsonb" },
      ],
      note: "Fallback géo si geo.api.gouv.fr KO",
    },
  },

  {
    id: "admins",
    type: "table",
    position: { x: 20, y: 700 },
    data: {
      name: "admins",
      cols: [
        { name: "id", type: "bigserial", tag: "PK" },
        { name: "username", type: "varchar", tag: "IX" },
        { name: "password_hash", type: "varchar" },
        { name: "role", type: "varchar" },
      ],
    },
  },
];

const EDGES: Edge[] = [
  {
    id: "fk-reg-dept",
    source: "regions",
    target: "departments",
    sourceHandle: "right",
    targetHandle: "left",
    label: "1..n",
  },
  {
    id: "fk-dept-city",
    source: "departments",
    target: "cities",
    sourceHandle: "right",
    targetHandle: "left",
    label: "1..n",
  },
  {
    id: "fk-city-tx",
    source: "cities",
    target: "transactions",
    sourceHandle: "right",
    targetHandle: "left",
    label: "1..n",
  },
  {
    id: "fk-city-stats",
    source: "cities",
    target: "city_dvf_yearly_stats",
    label: "1..n",
  },
  {
    id: "fk-city-quarterly",
    source: "cities",
    target: "city_price_quarterly",
    label: "1..n",
  },
  {
    id: "fk-dept-stats",
    source: "departments",
    target: "dept_dvf_stats",
    label: "1..1",
  },
  {
    id: "fk-tx-comp",
    source: "transactions",
    target: "comparable_transactions",
    sourceHandle: "right",
    targetHandle: "left",
    label: "1..n",
  },
];

const MONGO_INDEXES = [
  { name: "_id", purpose: "PK implicite" },
  { name: "cityInseeCode", purpose: "@Indexed — lookup principal" },
  { name: "content (text)", purpose: "@TextIndexed — full-text FR, stemming" },
];

const INDEX_RATIONALE = [
  {
    table: "transactions",
    name: "PK (id, mutation_date)",
    cols: "id, mutation_date",
    why: "Clé composite obligatoire dès qu'une table est partitionnée par mutation_date — Postgres exige la colonne de partition dans toute contrainte unique.",
  },
  {
    table: "transactions",
    name: "idx_transaction_mutation_id",
    cols: "mutation_id",
    why: "Dédup multi-lots DVF. Un même bien vendu via plusieurs lignes partage un mutation_id ; l'agrégateur groupe sur cet ID pour éviter de compter le bien N fois.",
  },
  {
    table: "transactions",
    name: "idx_transaction_mutation_date_brin",
    cols: "BRIN (mutation_date)",
    why: "BRIN compact (~10 KB / 20M rows) pour les requêtes date-range qui scannent une plage triée. La table est insérée par année (COPY into shadow partition) donc les pages physiques sont déjà ordonnées — BRIN est plus efficace qu'un B-tree dans ce cas.",
  },
  {
    table: "transactions",
    name: "idx_transaction_geocoded_bbox",
    cols: "(latitude, longitude) WHERE lat IS NOT NULL",
    why: "Heatmap viewport. Index partiel qui exclut les ~30% de rows non géocodés pour rester < 50 MB ; sert le predicate AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?.",
  },
  {
    table: "transactions",
    name: "idx_transaction_geocode_backlog",
    cols: "id WHERE latitude IS NULL",
    why: "Inverse du précédent : le job de geocoding fait un SELECT id FROM transactions WHERE latitude IS NULL LIMIT 1000 toutes les 30s. Sans cet index partiel, il ferait un seq scan complet.",
  },
  {
    table: "indicators",
    name: "idx_indicator_geo_category",
    cols: "(level, code, category)",
    why: "Hot path /api/indicators/{level}/{code}?category=X. Avant cet index, la query générait 200ms de latence p95 sur les pages ville ; ramené à 5ms.",
  },
  {
    table: "indicators",
    name: "idx_indicator_iris_code_prefix",
    cols: "code varchar_pattern_ops WHERE level='IRIS'",
    why: "Les IRIS d'une commune partagent le préfixe INSEE 5-chars (ex: '75101%' pour Paris 1er). varchar_pattern_ops permet à Postgres d'utiliser l'index pour un LIKE prefix sans full text search.",
  },
  {
    table: "cities",
    name: "idx_cities_name_trgm",
    cols: "GIN LOWER(name) gin_trgm_ops",
    why: "Autocomplete villes 'Sain' → 'Saint-Étienne'. pg_trgm GIN match les sous-chaînes ; sans lui, le LIKE prefix mature en seq scan dès le 2e caractère sur 35k rows.",
  },
  {
    table: "cities",
    name: "idx_cities_department_code",
    cols: "department_code",
    why: "Hot path /api/departments/{code}/cities. Sans cet index, la jointure communes ↔ département itère sur tous les 35k INSEE.",
  },
  {
    table: "city_dvf_yearly_stats",
    name: "idx_city_dvf_yearly_stats_year",
    cols: "year",
    why: "Le rebuild par année (CityDvfStatsAggregator.refreshYear) commence par un DELETE WHERE year = ?. Sans index, ce DELETE devient un full scan de la pré-agg (~70k rows × 10 ans).",
  },
  {
    table: "city_price_quarterly_stats",
    name: "idx_quarterly_year_q",
    cols: "(year, quarter)",
    why: "Timeline €/m² historique sur N années. La query SELECT … WHERE year IN (?,?,?) AND quarter IN (1,2,3,4) ORDER BY year,quarter parcours l'index trié au lieu de trier 4×N rows à la volée.",
  },
];

const REDIS_CACHES = [
  { name: "geo", ttl: "24 h", note: "GeoJSON pays + admin-1" },
  { name: "refdata", ttl: "12 h", note: "Régions / départements / villes" },
  { name: "stats", ttl: "30 min", note: "Stats agrégées par viewport" },
  { name: "reviews", ttl: "15 min", note: "Sentiment + extraits" },
];

export function DbSchemaPage() {
  return (
    <div className="space-y-10 pb-8">
      <section>
        <div className="flex items-baseline justify-between mb-3">
          <h2 className="text-lg font-semibold">PostgreSQL 16 (CloudNativePG)</h2>
          <span className="text-xs text-muted-foreground">17 changesets Liquibase</span>
        </div>
        <p className="text-sm text-muted-foreground mb-4">
          Diagramme ER live. <code className="font-mono text-xs">transactions</code> est
          partitionnée par année (2014..2030 + default) avec autovacuum tuné par partition (0.05 /
          0.02). Légende : <span className="text-amber-600 dark:text-amber-400">●</span> PK ·{" "}
          <span className="text-sky-600 dark:text-sky-400">○</span> FK ·{" "}
          <span className="text-violet-600 dark:text-violet-400">◇</span> Index.
        </p>

        <div className="h-[720px] rounded-lg border bg-muted/20">
          <ReactFlow
            nodes={NODES}
            edges={EDGES}
            nodeTypes={nodeTypes}
            fitView
            fitViewOptions={{ padding: 0.1 }}
            proOptions={{ hideAttribution: true }}
            nodesDraggable={false}
            nodesConnectable={false}
            elementsSelectable={false}
            zoomOnScroll={false}
            panOnScroll
          >
            <Background gap={20} />
            <Controls showInteractive={false} />
            <MiniMap pannable zoomable className="!bg-card" />
          </ReactFlow>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Index — choix techniques</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Pour chaque index non-trivial, le pattern de query qu'il sert et pourquoi cette stratégie
          a été retenue. Les index FK évidents (un par colonne <code>*_code</code>) sont omis.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Table</th>
                <th className="px-3 py-2 text-left">Index</th>
                <th className="px-3 py-2 text-left">Colonnes</th>
                <th className="px-3 py-2 text-left w-1/2">Raison</th>
              </tr>
            </thead>
            <tbody>
              {INDEX_RATIONALE.map((i) => (
                <tr key={`${i.table}-${i.name}`} className="border-t align-top">
                  <td className="px-3 py-2 font-mono text-xs whitespace-nowrap">{i.table}</td>
                  <td className="px-3 py-2 font-mono text-xs whitespace-nowrap">{i.name}</td>
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground whitespace-nowrap">
                    {i.cols}
                  </td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{i.why}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">MongoDB 7</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Une seule collection — les reviews avec sentiment. Schéma propre au{" "}
          <code>CityReview</code> entity (Spring Data Mongo applique les indexes au boot).
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
          Cache <code>@Cacheable</code> avec préfixe versionné <code>homepedia:v2:…</code>. Flush
          stale au boot ; sérialiseur Jackson avec class-info pour partager le pool entre apps.
        </p>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
          {REDIS_CACHES.map((c) => (
            <div key={c.name} className="rounded-md border bg-card px-3 py-2.5">
              <div className="font-mono font-semibold">{c.name}</div>
              <div className="text-muted-foreground mt-1">TTL {c.ttl}</div>
              <div className="text-muted-foreground mt-0.5 text-[10px]">{c.note}</div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
