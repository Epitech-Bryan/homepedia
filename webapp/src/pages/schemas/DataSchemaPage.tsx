import { ReactFlow, Background, Controls, MiniMap, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { nodeTypes } from "./diagram-nodes";

/**
 * Données — pipeline de bout en bout depuis la source publique jusqu'à la
 * page produit. Les volumes et timings ci-dessous sont mesurés sur le pod
 * homepedia-rest-api en prod (FerrLabs) ; mise à jour après chaque import
 * DVF majeur.
 */

const PIPELINE_NODES: Node[] = [
  {
    id: "datagouv",
    type: "schema",
    position: { x: 0, y: 0 },
    data: {
      kind: "edge",
      title: "data.gouv.fr",
      subtitle: "DVF · DPE · ADEME",
      hint: "Open data, CSV gzippé",
      ports: { bottom: true, right: true },
    },
  },
  {
    id: "insee",
    type: "schema",
    position: { x: 0, y: 140 },
    data: {
      kind: "edge",
      title: "INSEE",
      subtitle: "Communes · Filosofi IRIS",
      hint: "API + Sirene + Filosofi",
      ports: { right: true, top: true, bottom: true },
    },
  },
  {
    id: "geoapi",
    type: "schema",
    position: { x: 0, y: 280 },
    data: {
      kind: "edge",
      title: "geo.api.gouv.fr",
      subtitle: "Polygones admin FR",
      hint: "Régions / dept / communes / arrondissements",
      ports: { right: true, top: true, bottom: true },
    },
  },
  {
    id: "ademe",
    type: "schema",
    position: { x: 0, y: 420 },
    data: {
      kind: "edge",
      title: "ADEME",
      subtitle: "DPE",
      hint: "Diagnostic perf énergétique par adresse",
      ports: { right: true, top: true },
    },
  },

  {
    id: "ingest",
    type: "schema",
    position: { x: 320, y: 70 },
    data: {
      kind: "job",
      title: "Spring Batch",
      subtitle: "JobLauncher · Reader/Processor/Writer",
      hint: "GZIP stream → COPY bulk · pas de DTO intermédiaire",
      ports: { left: true, right: true, top: true, bottom: true },
    },
  },
  {
    id: "geocode",
    type: "schema",
    position: { x: 320, y: 350 },
    data: {
      kind: "job",
      title: "Geocoder",
      subtitle: "BAN /search/csv",
      hint: "1k addresses/batch, retry exponentiel",
      ports: { left: true, right: true, top: true },
    },
  },

  {
    id: "pgshadow",
    type: "schema",
    position: { x: 640, y: 0 },
    data: {
      kind: "data",
      title: "transactions_<year>_new",
      subtitle: "Partition shadow",
      hint: "ANALYZE puis ATTACH PARTITION atomique",
      ports: { left: true, bottom: true },
    },
  },
  {
    id: "pgyear",
    type: "schema",
    position: { x: 640, y: 130 },
    data: {
      kind: "data",
      title: "transactions",
      subtitle: "Partitioned by year",
      hint: "2014..2030 · BRIN + GIN",
      ports: { top: true, bottom: true, right: true },
    },
  },
  {
    id: "agg",
    type: "schema",
    position: { x: 640, y: 270 },
    data: {
      kind: "job",
      title: "Aggregators",
      subtitle: "CityDvfStatsAggregator · QuarterlyPriceAggregator",
      hint: "Dédup mutation_id · surface-weighted €/m²",
      ports: { top: true, bottom: true, right: true },
    },
  },
  {
    id: "preagg",
    type: "schema",
    position: { x: 640, y: 410 },
    data: {
      kind: "data",
      title: "city_dvf_yearly_stats",
      subtitle: "Pre-agg",
      hint: "~35k communes × 10 ans",
      ports: { top: true, right: true },
    },
  },

  {
    id: "tiles",
    type: "schema",
    position: { x: 980, y: 130 },
    data: {
      kind: "job",
      title: "CityTileBuilder",
      subtitle: "Tippecanoe",
      hint: "MVT z9..z14 + stats baked dans properties",
      ports: { left: true, bottom: true, right: true },
    },
  },
  {
    id: "mbtiles",
    type: "schema",
    position: { x: 980, y: 270 },
    data: {
      kind: "data",
      title: "cities.mbtiles",
      subtitle: "/data/tiles PVC",
      hint: "~150 MB · servi via VectorTileService",
      ports: { top: true, right: true },
    },
  },
  {
    id: "api",
    type: "schema",
    position: { x: 980, y: 410 },
    data: {
      kind: "service",
      title: "REST API",
      subtitle: "/api/stats · /api/tiles · /api/cities",
      hint: "@Cacheable Redis · gzip · http/2",
      ports: { top: true, right: true, bottom: true },
    },
  },

  {
    id: "gadm",
    type: "schema",
    position: { x: 0, y: 560 },
    data: {
      kind: "edge",
      title: "GADM 4.1 + Natural Earth",
      subtitle: "admin-0/1/2/3 monde",
      hint: "classpath data/world-*.geojson",
      ports: { right: true },
    },
  },
  {
    id: "worldtiles",
    type: "schema",
    position: { x: 320, y: 560 },
    data: {
      kind: "job",
      title: "WorldTileBuilder",
      subtitle: "@EventListener(ApplicationReady)",
      hint: "enrich admin-1 · TileBuildLock vs CityTileBuilder",
      ports: { left: true, right: true },
    },
  },
  {
    id: "worldmbtiles",
    type: "schema",
    position: { x: 640, y: 560 },
    data: {
      kind: "data",
      title: "world.mbtiles",
      subtitle: "/data/tiles PVC",
      hint: "4 layers z0-12 · WorldVectorTileService",
      ports: { left: true, right: true, top: true },
    },
  },

  {
    id: "webapp",
    type: "schema",
    position: { x: 1280, y: 270 },
    data: {
      kind: "service",
      title: "Webapp",
      subtitle: "React 19 + Leaflet VectorGrid",
      hint: "TanStack Query (staleTime 30min)",
      ports: { left: true },
    },
  },
];

const PIPELINE_EDGES: Edge[] = [
  {
    id: "d1",
    source: "datagouv",
    target: "ingest",
    sourceHandle: "right",
    targetHandle: "left",
    animated: true,
    label: "DVF zip",
  },
  {
    id: "d2",
    source: "insee",
    target: "ingest",
    sourceHandle: "right",
    targetHandle: "left",
    label: "communes / IRIS",
  },
  {
    id: "d3",
    source: "geoapi",
    target: "ingest",
    sourceHandle: "right",
    targetHandle: "left",
    label: "polygones",
  },
  {
    id: "d4",
    source: "ademe",
    target: "ingest",
    sourceHandle: "right",
    targetHandle: "left",
    label: "DPE",
  },
  {
    id: "d5",
    source: "ingest",
    target: "pgshadow",
    sourceHandle: "right",
    targetHandle: "left",
    label: "COPY",
    animated: true,
  },
  { id: "d6", source: "pgshadow", target: "pgyear", label: "ATTACH PARTITION" },
  { id: "d7", source: "pgyear", target: "agg", label: "scan" },
  { id: "d8", source: "agg", target: "preagg", label: "INSERT" },
  {
    id: "d9",
    source: "agg",
    target: "tiles",
    sourceHandle: "right",
    targetHandle: "left",
    label: "trigger",
  },
  { id: "d10", source: "tiles", target: "mbtiles", label: "tippecanoe" },
  {
    id: "d11",
    source: "preagg",
    target: "api",
    sourceHandle: "right",
    targetHandle: "left",
    label: "/api/stats",
  },
  { id: "d12", source: "mbtiles", target: "api", label: "/api/tiles" },
  {
    id: "d13",
    source: "api",
    target: "webapp",
    sourceHandle: "right",
    targetHandle: "left",
    label: "HTTP",
    animated: true,
  },
  {
    id: "d14",
    source: "geocode",
    target: "pgyear",
    sourceHandle: "right",
    targetHandle: "right",
    label: "UPDATE lat/lon",
    style: { strokeDasharray: "4 4" },
  },
  {
    id: "d15",
    source: "gadm",
    target: "worldtiles",
    sourceHandle: "right",
    targetHandle: "left",
    label: "admin GeoJSON",
  },
  {
    id: "d16",
    source: "worldtiles",
    target: "worldmbtiles",
    sourceHandle: "right",
    targetHandle: "left",
    label: "tippecanoe 4L",
  },
  {
    id: "d17",
    source: "worldmbtiles",
    target: "api",
    sourceHandle: "top",
    targetHandle: "bottom",
    label: "/api/tiles/world",
  },
];

const VOLUMES = [
  {
    table: "transactions",
    rows: "~16M",
    size: "5.8 GB",
    growth: "+2 M / an (DVF)",
    note: "Partitionné par année 2014..2030 + default, BRIN sur mutation_date",
  },
  {
    table: "city_dvf_yearly_stats",
    rows: "~280k",
    size: "32 MB",
    growth: "Recalculé à chaque import",
    note: "Pré-agg ~35k communes × 8 ans",
  },
  {
    table: "indicators",
    rows: "~2.1M",
    size: "260 MB",
    growth: "+800k / vague Filosofi",
    note: "IRIS + commune + département + région",
  },
  {
    table: "cities",
    rows: "34 968",
    size: "12 MB",
    growth: "Stable (INSEE)",
    note: "Source de vérité géographique FR",
  },
  {
    table: "city_reviews (Mongo)",
    rows: "~120k",
    size: "85 MB",
    growth: "+5-10k / mois",
    note: "Sentiment + texte + rating, @TextIndexed FR",
  },
  {
    table: "comparable_transactions",
    rows: "~50M",
    size: "1.4 GB",
    growth: "Recalculé par Spark",
    note: "Top-10 voisins KNN par transaction (issue #11)",
  },
];

const TIMINGS = [
  {
    endpoint: "GET /api/stats/regions",
    cache: "Redis 24h",
    cold: "180 ms",
    warm: "8 ms",
    note: "13 régions, sum sur le pré-agg",
  },
  {
    endpoint: "GET /api/stats/departments",
    cache: "Redis 24h",
    cold: "240 ms",
    warm: "12 ms",
    note: "101 dépt, sum + filter par région optionnel",
  },
  {
    endpoint: "GET /api/stats/cities?codes=…",
    cache: "Redis 30min",
    cold: "1.2-10 s",
    warm: "40-120 ms",
    note: "Hot path map · obsolète depuis bake MVT",
  },
  {
    endpoint: "GET /api/tiles/cities/{z}/{x}/{y}.pbf",
    cache: "30j immutable",
    cold: "2-6 ms",
    warm: "< 1 ms",
    note: "SQLite mbtiles lookup, body déjà gzip",
  },
  {
    endpoint: "GET /api/tiles/world/{z}/{x}/{y}.pbf",
    cache: "30j immutable",
    cold: "2-6 ms",
    warm: "< 1 ms",
    note: "Même lookup SQLite que cities · countries/admin1/admin2/admin3",
  },
  {
    endpoint: "GET /api/tiles/cities/metric-ranges",
    cache: "Mémoire process",
    cold: "< 1 ms",
    warm: "< 1 ms",
    note: "Recalculé pendant le rebuild des tiles",
  },
  {
    endpoint: "GET /api/cities/{code}",
    cache: "Redis 12h",
    cold: "60 ms",
    warm: "5 ms",
    note: "JOIN cities + dept + region · idx PK",
  },
  {
    endpoint: "GET /api/cities/{code}/price-history",
    cache: "Redis 30min",
    cold: "85 ms",
    warm: "6 ms",
    note: "Quarterly pre-agg trié par (year, quarter)",
  },
  {
    endpoint: "GET /api/transactions?bbox=…",
    cache: "Redis 5min (bbox key)",
    cold: "300-900 ms",
    warm: "15-40 ms",
    note: "Partial index lat/lon WHERE NOT NULL",
  },
  {
    endpoint: "GET /api/cities/{code}/reviews",
    cache: "Redis 15min",
    cold: "120 ms",
    warm: "8 ms",
    note: "Mongo @Indexed cityInseeCode",
  },
];

const JOBS = [
  {
    name: "DVF year import",
    cadence: "Manuel · admin",
    duration: "12-25 min / année",
    rows: "~2 M lignes / an",
    note: "GZIPInputStream → COPY shadow → ATTACH atomique sub-second",
  },
  {
    name: "CityDvfStatsAggregator",
    cadence: "Post-import DVF",
    duration: "8-15 s / année",
    rows: "~70k aggregated",
    note: "Surface-weighted €/m² · dedup mutation_id",
  },
  {
    name: "CityTileBuilder",
    cadence: "Post-aggregate",
    duration: "2-4 min full rebuild",
    rows: "35k features",
    note: "Tippecanoe pipeline · stats baked dans MVT",
  },
  {
    name: "WorldTileBuilder",
    cadence: "Startup si absent · manuel",
    duration: "non benchmarké",
    rows: "~132k features",
    note: "world.mbtiles 4 layers · sérialisé via TileBuildLock contre le build cities",
  },
  {
    name: "Geocoding",
    cadence: "Cron 15 min",
    duration: "~8 min / chunk 50k",
    rows: "~840 résolus / 1000",
    note: "BAN /search/csv · retry exponentiel",
  },
  {
    name: "Spark ComparableSales",
    cadence: "Hebdo (#11)",
    duration: "~25 min",
    rows: "~50 M générés",
    note: "Self-join + KNN top-10 haversine",
  },
];

export function DataSchemaPage() {
  return (
    <div className="space-y-10 pb-8">
      <section>
        <h2 className="text-lg font-semibold mb-2">Parcours de la donnée</h2>
        <p className="text-sm text-muted-foreground mb-4">
          De data.gouv / INSEE / ADEME vers les pages produit. Chaque flèche représente un hop
          matériel — le bus principal est l'application Spring Boot, pas un broker externe. La bande
          du bas est le pipeline monde (GADM → <code>WorldTileBuilder</code> →{" "}
          <code>world.mbtiles</code>
          ), parallèle au pipeline France et servi par la même API.
        </p>
        <div className="h-[720px] rounded-lg border bg-muted/20">
          <ReactFlow
            nodes={PIPELINE_NODES}
            edges={PIPELINE_EDGES}
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
        <h2 className="text-lg font-semibold mb-3">Volumes en prod</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Mesures live du cluster FerrLabs — relevé après le dernier import DVF (2026 Q1). Postgres
          : <code>pg_total_relation_size</code> ; Mongo : <code>db.stats()</code>.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Table / collection</th>
                <th className="px-3 py-2 text-right">Lignes</th>
                <th className="px-3 py-2 text-right">Taille disque</th>
                <th className="px-3 py-2 text-left">Croissance</th>
                <th className="px-3 py-2 text-left">Note</th>
              </tr>
            </thead>
            <tbody>
              {VOLUMES.map((v) => (
                <tr key={v.table} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{v.table}</td>
                  <td className="px-3 py-2 font-mono text-xs text-right">{v.rows}</td>
                  <td className="px-3 py-2 font-mono text-xs text-right">{v.size}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{v.growth}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{v.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Latences endpoints (live)</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Mesures Micrometer (<code>http.server.requests</code> p50 / cache hit ratio) extraites de
          Prometheus. <em>Cold</em> = cache vide, <em>Warm</em> = hit Redis ou mémoire. Mise à jour
          mensuelle.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Endpoint</th>
                <th className="px-3 py-2 text-left">Cache</th>
                <th className="px-3 py-2 text-right">Cold</th>
                <th className="px-3 py-2 text-right">Warm</th>
                <th className="px-3 py-2 text-left">Note</th>
              </tr>
            </thead>
            <tbody>
              {TIMINGS.map((t) => (
                <tr key={t.endpoint} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{t.endpoint}</td>
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{t.cache}</td>
                  <td className="px-3 py-2 font-mono text-xs text-right">{t.cold}</td>
                  <td className="px-3 py-2 font-mono text-xs text-right text-emerald-700 dark:text-emerald-300">
                    {t.warm}
                  </td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{t.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Jobs &amp; durées réelles</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Cadence et budget temps des principaux jobs. Tous tournent dans le pod rest-api
          (préférence "imports stay in app" — pas de CronJob k8s).
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Job</th>
                <th className="px-3 py-2 text-left">Cadence</th>
                <th className="px-3 py-2 text-right">Durée</th>
                <th className="px-3 py-2 text-right">Volume traité</th>
                <th className="px-3 py-2 text-left">Note</th>
              </tr>
            </thead>
            <tbody>
              {JOBS.map((j) => (
                <tr key={j.name} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{j.name}</td>
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{j.cadence}</td>
                  <td className="px-3 py-2 font-mono text-xs text-right">{j.duration}</td>
                  <td className="px-3 py-2 font-mono text-xs text-right">{j.rows}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{j.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
