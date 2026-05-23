import { ReactFlow, Background, Controls, MiniMap, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { nodeTypes } from "./diagram-nodes";

/**
 * Spark — deux jobs (DvfAggregateJob et ComparableSalesAggregateJob) qui
 * tournent en out-of-band du pod rest-api. Stocke leurs résultats dans
 * Postgres ; les endpoints REST lisent directement les tables matérialisées.
 */

const SPARK_NODES: Node[] = [
  {
    id: "dvf-csv",
    type: "schema",
    position: { x: 0, y: 0 },
    data: {
      kind: "edge",
      title: "DVF CSV",
      subtitle: "PVC /data/dvf/",
      hint: "~20 M lignes/historique · header + 45 cols",
      ports: { right: true, bottom: true },
    },
  },
  {
    id: "pg-cities",
    type: "schema",
    position: { x: 0, y: 140 },
    data: {
      kind: "data",
      title: "Postgres · cities",
      subtitle: "insee_code → department_code",
      hint: "~35 k rows · broadcast côté Spark",
      ports: { right: true, top: true, bottom: true },
    },
  },
  {
    id: "pg-tx",
    type: "schema",
    position: { x: 0, y: 280 },
    data: {
      kind: "data",
      title: "Postgres · transactions",
      subtitle: "Partitioned by year",
      hint: "~6 M rows géocodés · lus en 16 partitions JDBC",
      ports: { right: true, top: true },
    },
  },

  {
    id: "dvf-job",
    type: "schema",
    position: { x: 360, y: 70 },
    data: {
      kind: "job",
      title: "DvfAggregateJob",
      subtitle: "homepedia-dvf-aggregate",
      hint: "Broadcast join · AQE · Kryo · coalesce(1) write",
      ports: { left: true, right: true, bottom: true },
    },
  },
  {
    id: "comp-job",
    type: "schema",
    position: { x: 360, y: 280 },
    data: {
      kind: "job",
      title: "ComparableSalesAggregateJob",
      subtitle: "homepedia-comparable-sales",
      hint: "Self-join bucketé · skew handling · off-heap 1G",
      ports: { left: true, right: true, top: true },
    },
  },

  {
    id: "dept-stats",
    type: "schema",
    position: { x: 760, y: 70 },
    data: {
      kind: "data",
      title: "dept_dvf_stats",
      subtitle: "~100 rows",
      hint: "Une connexion JDBC unique (coalesce 1)",
      ports: { left: true, right: true },
    },
  },
  {
    id: "comparable",
    type: "schema",
    position: { x: 760, y: 280 },
    data: {
      kind: "data",
      title: "comparable_transactions",
      subtitle: "~50 M rows",
      hint: "Top-10 voisins · (tx_id, rank, distance_m, delta%)",
      ports: { left: true, right: true },
    },
  },

  {
    id: "rest-api",
    type: "schema",
    position: { x: 1100, y: 170 },
    data: {
      kind: "service",
      title: "REST API",
      subtitle: "/api/transactions/{id}/comparable-sales · /api/stats/departments",
      hint: "@Cacheable Redis · réponses directes",
      ports: { left: true },
    },
  },
];

const SPARK_EDGES: Edge[] = [
  {
    id: "s1",
    source: "dvf-csv",
    target: "dvf-job",
    sourceHandle: "right",
    targetHandle: "left",
    label: "spark.read.csv",
    animated: true,
  },
  {
    id: "s2",
    source: "pg-cities",
    target: "dvf-job",
    sourceHandle: "right",
    targetHandle: "left",
    label: "broadcast(cities)",
  },
  {
    id: "s3",
    source: "pg-tx",
    target: "comp-job",
    sourceHandle: "right",
    targetHandle: "left",
    label: "JDBC 16-partition",
    animated: true,
  },
  {
    id: "s4",
    source: "dvf-job",
    target: "dept-stats",
    sourceHandle: "right",
    targetHandle: "left",
    label: "Overwrite",
  },
  {
    id: "s5",
    source: "comp-job",
    target: "comparable",
    sourceHandle: "right",
    targetHandle: "left",
    label: "Overwrite",
  },
  {
    id: "s6",
    source: "dept-stats",
    target: "rest-api",
    sourceHandle: "right",
    targetHandle: "left",
    label: "SELECT",
  },
  {
    id: "s7",
    source: "comparable",
    target: "rest-api",
    sourceHandle: "right",
    targetHandle: "left",
    label: "SELECT",
  },
];

const DVF_PHASES = [
  {
    phase: "1. Read CSV",
    detail: "inferSchema=false · projection 4 cols · multiLine=false",
    why: "Évite le double-scan de inferSchema (~30s) et le buffering quoted-blocks",
  },
  {
    phase: "2. Filter pre-join",
    detail: "price IS NOT NULL AND price > 0",
    why: "Push down avant le join — pas de shuffle de lignes inutilisables",
  },
  {
    phase: "3. Broadcast join",
    detail: "functions.broadcast(cities) — ~350 kB",
    why: "Évite le shuffle des 20 M lignes DVF sur insee_code (~1 min économisé)",
  },
  {
    phase: "4. Enrich",
    detail: "price_per_sqm = price / surface (when surface > 0)",
    why: "Une seule passe, dérivé en flight",
  },
  {
    phase: "5. groupBy(department)",
    detail: "count · avg(price) · avg(€/m²) · percentile_approx(0.5)",
    why: "Aggregation Spark native, sortie ~100 rows",
  },
  {
    phase: "6. coalesce(1).write",
    detail: "Une partition · une connexion JDBC",
    why: "Évite 200 connexions ouvertes/fermées pour 100 rows total",
  },
];

const COMP_PHASES = [
  {
    phase: "1. JDBC partitionné",
    detail: "partitionColumn=id, numPartitions=16, fetchsize=10k",
    why: "16 sockets parallèles plutôt qu'un seul TCP (~3 min économisés)",
  },
  {
    phase: "2. Pré-filter",
    detail: "lat/lon non-null · 10k < price < 5M · 9 ≤ surface ≤ 1000",
    why: "Élimine les comparables dégénérés que le popup ne saurait afficher",
  },
  {
    phase: "3. Bucketing",
    detail: "(property_type, year, surface/20m², geohash6 lat/lon ×100)",
    why: "Cellules ~1.2km × 0.6km · réduit le self-join à des paires locales",
  },
  {
    phase: "4. repartition + cache",
    detail: "repartition par les 5 clés de bucket · .cache()",
    why: "Bucket co-localisé sur un exécuteur · lu 2× (alias l + r)",
  },
  {
    phase: "5. Self-join",
    detail: "l ⨝ r ON (mêmes 5 buckets) AND l.id ≠ r.id",
    why: "Cartésien à l'intérieur du bucket seulement, pas globalement",
  },
  {
    phase: "6. Haversine + rank",
    detail: "row_number() OVER (PARTITION BY l.id ORDER BY distance_m)",
    why: "Top-10 voisins par transaction, déterministe",
  },
  {
    phase: "7. write Overwrite",
    detail: "SaveMode.Overwrite sur comparable_transactions",
    why: "Idempotence · re-run après import DVF ne compose pas de stale",
  },
];

const SPARK_TUNING = [
  {
    setting: "spark.sql.adaptive.enabled = true",
    rationale: "AQE coalesce partitions vides, switch broadcast à runtime, split skewed partitions",
    applies: "DvfAggregateJob + ComparableSalesAggregateJob",
  },
  {
    setting: "spark.sql.adaptive.skewJoin.skewedPartitionFactor = 5",
    rationale: "Default 10× laisse les buckets parisiens OOM sur un exécuteur ; 5× split plus tôt",
    applies: "ComparableSalesAggregateJob (self-join géo-skewé)",
  },
  {
    setting: "spark.sql.shuffle.partitions = 64 / 400",
    rationale: "64 suffit pour ~100 rows DVF agrégés ; 400 absorbe le fan-out du self-join",
    applies: "DVF (64) · Comparable (400)",
  },
  {
    setting: "spark.serializer = KryoSerializer",
    rationale: "~2× plus rapide que Java sur les Row à doubles (lat/lon/price/buckets)",
    applies: "Les deux",
  },
  {
    setting: "spark.memory.offHeap.size = 1g",
    rationale: "Sort-merge spills hors heap JVM, évite la concurrence avec l'exécuteur",
    applies: "ComparableSalesAggregateJob",
  },
  {
    setting: "spark.shuffle.compress = true",
    rationale: "Réduit la pression disque par exécuteur (~12 GB shuffle total)",
    applies: "ComparableSalesAggregateJob",
  },
  {
    setting: "functions.broadcast(cities)",
    rationale: "Forçage explicite, ne dépend pas de l'autobroadcastThreshold",
    applies: "DvfAggregateJob",
  },
  {
    setting: "coalesce(1) avant JDBC write",
    rationale: "Output ~100 rows · une connexion suffit, évite N open/close cycles",
    applies: "DvfAggregateJob",
  },
];

const SPARK_OUTPUTS = [
  {
    table: "dept_dvf_stats",
    cols: "department_code · transaction_count · avg_price · avg_price_per_sqm · median_price",
    rows: "~100",
    consumer: "/api/stats/departments",
    note: "SaveMode.Overwrite · TRUNCATE puis INSERT atomique",
  },
  {
    table: "comparable_transactions",
    cols: "transaction_id · similarity_rank 1..10 · comparable_id · distance_m · price_delta_pct · updated_at",
    rows: "~50 M",
    consumer: "/api/transactions/{id}/comparable-sales",
    note: "Idempotent · re-run hebdo",
  },
];

const RUN_OPS = [
  {
    when: "Setup local",
    cmd: "mvn -pl backend/spark-jobs package",
    note: "Produit le fat-jar avec dépendances Postgres",
  },
  {
    when: "Run DVF aggregate",
    cmd: "spark-submit --class com.homepedia.spark.DvfAggregateJob spark-jobs.jar --input-path /data/dvf/full.csv --jdbc-url jdbc:postgresql://...",
    note: "Args : --input-path, --jdbc-url, --jdbc-user, --jdbc-password",
  },
  {
    when: "Run Comparable Sales",
    cmd: "spark-submit --class com.homepedia.spark.ComparableSalesAggregateJob spark-jobs.jar --jdbc-url ... --jdbc-user ... --jdbc-password ...",
    note: "Pas de --input-path · lit transactions depuis Postgres",
  },
];

export function SparkSchemaPage() {
  return (
    <div className="space-y-10 pb-8">
      <section>
        <h2 className="text-lg font-semibold mb-2">Vue d'ensemble du traitement Spark</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Deux jobs distincts : <code>DvfAggregateJob</code> agrège les CSV DVF par département,{" "}
          <code>ComparableSalesAggregateJob</code> calcule le top-10 des ventes comparables par
          transaction. Les deux écrivent dans Postgres via JDBC et l'API REST y lit directement.
        </p>
        <div className="h-[480px] rounded-lg border bg-muted/20">
          <ReactFlow
            nodes={SPARK_NODES}
            edges={SPARK_EDGES}
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
        <h2 className="text-lg font-semibold mb-3">DvfAggregateJob — étapes</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Lecture du CSV DVF, join avec la table <code>cities</code> (broadcast), agrégation par
          département, écriture finale en une seule connexion JDBC.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Phase</th>
                <th className="px-3 py-2 text-left">Détail</th>
                <th className="px-3 py-2 text-left">Pourquoi</th>
              </tr>
            </thead>
            <tbody>
              {DVF_PHASES.map((p) => (
                <tr key={p.phase} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{p.phase}</td>
                  <td className="px-3 py-2 font-mono text-xs">{p.detail}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{p.why}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">ComparableSalesAggregateJob — étapes</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Self-join bucketé géographiquement (geohash6) et par typologie/surface/année. Chaque
          transaction garde ses 10 plus proches voisins par Haversine. Tournée hebdomadaire, ~25 min
          sur ~6 M transactions géocodées.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Phase</th>
                <th className="px-3 py-2 text-left">Détail</th>
                <th className="px-3 py-2 text-left">Pourquoi</th>
              </tr>
            </thead>
            <tbody>
              {COMP_PHASES.map((p) => (
                <tr key={p.phase} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{p.phase}</td>
                  <td className="px-3 py-2 font-mono text-xs">{p.detail}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{p.why}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Tuning Spark appliqué</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Chaque réglage a une raison mesurée — pas de cargo cult. Les valeurs par défaut Spark sont
          optimisées pour des clusters de plus de 100 nœuds, on tourne sur 4 exécuteurs max.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Réglage</th>
                <th className="px-3 py-2 text-left">Pourquoi</th>
                <th className="px-3 py-2 text-left">S'applique à</th>
              </tr>
            </thead>
            <tbody>
              {SPARK_TUNING.map((t) => (
                <tr key={t.setting} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{t.setting}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{t.rationale}</td>
                  <td className="px-3 py-2 font-mono text-xs">{t.applies}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Sorties Postgres</h2>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Table</th>
                <th className="px-3 py-2 text-left">Colonnes</th>
                <th className="px-3 py-2 text-right">Rows</th>
                <th className="px-3 py-2 text-left">Consumer</th>
                <th className="px-3 py-2 text-left">Note</th>
              </tr>
            </thead>
            <tbody>
              {SPARK_OUTPUTS.map((o) => (
                <tr key={o.table} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{o.table}</td>
                  <td className="px-3 py-2 font-mono text-[10px] text-muted-foreground">
                    {o.cols}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs text-right">{o.rows}</td>
                  <td className="px-3 py-2 font-mono text-xs">{o.consumer}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{o.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Exécution</h2>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Étape</th>
                <th className="px-3 py-2 text-left">Commande</th>
                <th className="px-3 py-2 text-left">Note</th>
              </tr>
            </thead>
            <tbody>
              {RUN_OPS.map((r) => (
                <tr key={r.when} className="border-t">
                  <td className="px-3 py-2 text-xs font-medium">{r.when}</td>
                  <td className="px-3 py-2 font-mono text-[10px] break-all">{r.cmd}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{r.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
