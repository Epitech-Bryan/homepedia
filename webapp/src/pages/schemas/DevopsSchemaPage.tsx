import { ReactFlow, Background, Controls, MiniMap, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { nodeTypes } from "./diagram-nodes";

/**
 * DevOps / GitOps reference. Reflects the live FerrLabs cluster topology and
 * the GitHub Actions → GHCR → Flux pipeline. This page replaces the
 * docker-compose write-up; the compose file only runs locally for dev now,
 * everything in production is K8s.
 */

const PIPELINE_NODES: Node[] = [
  {
    id: "dev",
    type: "schema",
    position: { x: 0, y: 0 },
    data: {
      kind: "edge",
      title: "Local dev",
      subtitle: "compose.yml",
      hint: "Postgres + Mongo + Redis + Spark, pas Traefik",
      ports: { bottom: true, right: true },
    },
  },
  {
    id: "github",
    type: "schema",
    position: { x: 280, y: 0 },
    data: {
      kind: "edge",
      title: "GitHub · main",
      subtitle: "Conventional commits",
      hint: "Tag webapp-v* / rest-api-v* déclenche release",
      ports: { left: true, right: true, bottom: true },
    },
  },
  {
    id: "actions",
    type: "schema",
    position: { x: 540, y: 0 },
    data: {
      kind: "infra",
      title: "GitHub Actions",
      subtitle: ".github/workflows",
      hint: "Lint + tests + build (paths-filter)",
      ports: { left: true, right: true },
    },
  },
  {
    id: "ghcr",
    type: "schema",
    position: { x: 820, y: 0 },
    data: {
      kind: "edge",
      title: "GHCR",
      subtitle: "ghcr.io/epitech-bryan/homepedia",
      hint: "Image push à chaque tag",
      ports: { left: true, bottom: true },
    },
  },

  {
    id: "image-reflector",
    type: "schema",
    position: { x: 820, y: 140 },
    data: {
      kind: "infra",
      title: "image-reflector-controller",
      subtitle: "Flux",
      hint: "Scan GHCR toutes les 5 min",
      ports: { top: true, bottom: true },
    },
  },
  {
    id: "image-update",
    type: "schema",
    position: { x: 820, y: 280 },
    data: {
      kind: "infra",
      title: "image-update-automation",
      subtitle: "Flux",
      hint: "git commit -> bryan-platform/kubernetes",
      ports: { top: true, bottom: true, left: true },
    },
  },
  {
    id: "k8s-repo",
    type: "schema",
    position: { x: 540, y: 280 },
    data: {
      kind: "edge",
      title: "kubernetes-manifests repo",
      subtitle: "homepedia-*-helmrelease.yaml",
      hint: "Source of truth (GitOps)",
      ports: { left: true, right: true },
    },
  },
  {
    id: "source",
    type: "schema",
    position: { x: 280, y: 280 },
    data: {
      kind: "infra",
      title: "source-controller",
      subtitle: "Flux",
      hint: "GitRepository poll 1 min",
      ports: { left: true, right: true, bottom: true },
    },
  },
  {
    id: "kustomize",
    type: "schema",
    position: { x: 280, y: 420 },
    data: {
      kind: "infra",
      title: "kustomize-controller",
      subtitle: "Flux",
      hint: "Render + apply Kustomization",
      ports: { top: true, bottom: true },
    },
  },
  {
    id: "helm",
    type: "schema",
    position: { x: 540, y: 420 },
    data: {
      kind: "infra",
      title: "helm-controller",
      subtitle: "Flux",
      hint: "Render HelmRelease → Deployment + Svc",
      ports: { left: true, bottom: true },
    },
  },
  {
    id: "cluster",
    type: "schema",
    position: { x: 410, y: 560 },
    data: {
      kind: "service",
      title: "homepedia namespace",
      subtitle: "homepedia-rest-api · homepedia-webapp · mongodb-0",
      hint: "Pods replaced, readiness probes gate the swap",
      ports: { top: true },
    },
  },
];

const PIPELINE_EDGES: Edge[] = [
  { id: "p1", source: "dev", target: "github", animated: true, label: "git push" },
  { id: "p2", source: "github", target: "actions", label: "PR / tag" },
  { id: "p3", source: "actions", target: "ghcr", label: "docker push", animated: true },
  {
    id: "p4",
    source: "ghcr",
    target: "image-reflector",
    label: "poll",
  },
  {
    id: "p5",
    source: "image-reflector",
    target: "image-update",
    label: "new tag",
  },
  {
    id: "p6",
    source: "image-update",
    target: "k8s-repo",
    sourceHandle: "left",
    targetHandle: "right",
    label: "bump tag",
    animated: true,
  },
  {
    id: "p7",
    source: "k8s-repo",
    target: "source",
    sourceHandle: "left",
    targetHandle: "right",
    label: "GitRepository",
  },
  {
    id: "p8",
    source: "source",
    target: "kustomize",
    label: "artifact",
  },
  {
    id: "p9",
    source: "kustomize",
    target: "helm",
    sourceHandle: "right",
    targetHandle: "left",
    label: "HelmRelease",
  },
  {
    id: "p10",
    source: "helm",
    target: "cluster",
    label: "apply",
    animated: true,
  },
];

const CLUSTER_NODES: Node[] = [
  {
    id: "ingress-group",
    type: "group",
    position: { x: 0, y: 0 },
    data: {
      label: "ingress / network",
      width: 360,
      height: 140,
      tint: "bg-sky-500/5 border-sky-500/30",
    },
    draggable: false,
    selectable: false,
  },
  {
    id: "platform-group",
    type: "group",
    position: { x: 400, y: 0 },
    data: {
      label: "platform",
      width: 360,
      height: 140,
      tint: "bg-violet-500/5 border-violet-500/30",
    },
    draggable: false,
    selectable: false,
  },
  {
    id: "storage-group",
    type: "group",
    position: { x: 0, y: 180 },
    data: { label: "storage", width: 360, height: 140, tint: "bg-amber-500/5 border-amber-500/30" },
    draggable: false,
    selectable: false,
  },
  {
    id: "data-group",
    type: "group",
    position: { x: 400, y: 180 },
    data: {
      label: "stateful workloads",
      width: 360,
      height: 140,
      tint: "bg-emerald-500/5 border-emerald-500/30",
    },
    draggable: false,
    selectable: false,
  },

  {
    id: "metallb",
    type: "schema",
    position: { x: 20, y: 30 },
    data: {
      kind: "infra",
      title: "MetalLB",
      subtitle: "L2 mode",
      ports: { right: true, top: false, bottom: false },
    },
  },
  {
    id: "traefik",
    type: "schema",
    position: { x: 200, y: 30 },
    data: {
      kind: "infra",
      title: "Traefik v3",
      subtitle: "IngressRoute + LoadBalancer Svc",
      ports: { left: true, top: false, bottom: false },
    },
  },

  {
    id: "certmanager",
    type: "schema",
    position: { x: 420, y: 30 },
    data: {
      kind: "infra",
      title: "cert-manager",
      subtitle: "Let's Encrypt + DNS-01",
      ports: { top: false, bottom: false },
    },
  },
  {
    id: "vault",
    type: "schema",
    position: { x: 600, y: 30 },
    data: {
      kind: "infra",
      title: "Vault + Agent Injector",
      subtitle: "KV v2",
      ports: { top: false, bottom: false },
    },
  },

  {
    id: "longhorn",
    type: "schema",
    position: { x: 20, y: 210 },
    data: {
      kind: "data",
      title: "Longhorn",
      subtitle: "longhorn-single SC",
      ports: { top: false, bottom: false },
    },
  },
  {
    id: "pvc-tiles",
    type: "schema",
    position: { x: 200, y: 210 },
    data: {
      kind: "data",
      title: "PVC · /data/tiles",
      subtitle: "1 Gi · rest-api",
      ports: { top: false, bottom: false },
    },
  },

  {
    id: "cnpg",
    type: "schema",
    position: { x: 420, y: 210 },
    data: {
      kind: "data",
      title: "CloudNativePG",
      subtitle: "timescaledb cluster",
      ports: { top: false, bottom: false },
    },
  },
  {
    id: "mongo",
    type: "schema",
    position: { x: 600, y: 210 },
    data: {
      kind: "data",
      title: "MongoDB StatefulSet",
      subtitle: "1 replica · Longhorn",
      ports: { top: false, bottom: false },
    },
  },
];

const CI_JOBS = [
  { name: "lint-webapp", trigger: "webapp/**", what: "pnpm exec eslint + prettier --check" },
  { name: "test-webapp", trigger: "webapp/**", what: "Vitest unit + jsdom" },
  { name: "build-webapp", trigger: "webapp/**", what: "vite build (vérifie aussi tsc -b)" },
  { name: "lint-backend", trigger: "backend/**", what: "mvn spotless:check" },
  {
    name: "test-backend",
    trigger: "backend/**",
    what: "mvn verify -Pintegration (PG + Mongo + Redis Testcontainers)",
  },
  { name: "build-backend", trigger: "backend/**", what: "mvn package, jar runtime" },
  {
    name: "release",
    trigger: "tag webapp-v*/rest-api-v*",
    what: "Docker buildx multi-arch → GHCR",
  },
  {
    name: "flux-image-automation",
    trigger: "GHCR push",
    what: "image-update bump dans kubernetes-manifests",
  },
];

const PIPELINES = [
  {
    title: "DVF (Demandes Valeurs Foncières)",
    steps: [
      "Téléchargement gzip data.gouv (~2 GB / an)",
      "GZIPInputStream → COPY transactions_<year>_new (shadow)",
      "ATTACH PARTITION atomique (sub-second)",
      "ANALYZE transactions_<year>",
      "CityDvfStatsAggregator.refreshYear",
      "CityQuarterlyPriceAggregator.refreshYear",
      "CityTileBuilder.rebuildAsync → /data/tiles/cities.mbtiles",
    ],
  },
  {
    title: "INSEE Filosofi (IRIS)",
    steps: [
      "Pull Filosofi CSV (~50k IRIS × 30 cols)",
      "Filtre indicateurs (revenu médian, pauvreté, gini…)",
      "Insert dans indicators (geographic_level='IRIS')",
      "Spring Batch + admin trigger + cron mensuel",
    ],
  },
  {
    title: "Spark — ComparableSalesAggregator (#11)",
    steps: [
      "Spark SQL : transactions geocodées des 5 dernières années",
      "Self-join sur property_type + surface ±15% + année ±1",
      "KNN top-10 par distance haversine + ratio prix/m²",
      "COPY bulk dans comparable_transactions",
    ],
  },
  {
    title: "World tiles (GADM)",
    steps: [
      "ApplicationReadyEvent → WorldTileBuilder si world.mbtiles absent",
      "Enrich admin-1 avec world-admin1-metrics.json (pop / area / gdp)",
      "Tippecanoe 4 layers : countries z0-4, admin1 z5-7, admin2 z8-10, admin3 z11-12",
      "Move atomique → /data/tiles/world.mbtiles + WorldVectorTileService.reload()",
      "TileBuildLock sérialise contre CityTileBuilder (pas de pic disque simultané)",
    ],
  },
];

export function DevopsSchemaPage() {
  return (
    <div className="space-y-10 pb-8">
      <section>
        <h2 className="text-lg font-semibold mb-2">Pipeline GitOps</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Push sur <code>main</code> → GitHub Actions → image GHCR → Flux <em>image-update</em>{" "}
          réécrit le tag dans <code>kubernetes-manifests</code> → <em>kustomize-controller</em> +{" "}
          <em>helm-controller</em> appliquent le HelmRelease. Aucun <code>kubectl apply</code>{" "}
          manuel ; les changements directs sur le cluster sont écrasés sous 5 min.
        </p>
        <div className="h-[680px] rounded-lg border bg-muted/20">
          <ReactFlow
            nodes={PIPELINE_NODES}
            edges={PIPELINE_EDGES}
            nodeTypes={nodeTypes}
            fitView
            fitViewOptions={{ padding: 0.15 }}
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
        <h2 className="text-lg font-semibold mb-2">Topologie cluster (FerrLabs)</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Mono-node k3s/kubeadm sur VPS. Tout passe par Traefik exposé via MetalLB. Le storage est
          assuré par Longhorn ; les bases stateful tournent via leur opérateur (CNPG pour Postgres).
        </p>
        <div className="h-[400px] rounded-lg border bg-muted/20">
          <ReactFlow
            nodes={CLUSTER_NODES}
            edges={[]}
            nodeTypes={nodeTypes}
            fitView
            fitViewOptions={{ padding: 0.15 }}
            proOptions={{ hideAttribution: true }}
            nodesDraggable={false}
            nodesConnectable={false}
            elementsSelectable={false}
            zoomOnScroll={false}
            panOnScroll
          >
            <Background gap={20} />
            <Controls showInteractive={false} />
          </ReactFlow>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">CI/CD — GitHub Actions</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Jobs scopés via <code>paths</code> sur les fichiers changés — un PR webapp ne déclenche
          pas les tests backend, et vice-versa. Tag git déclenche release multi-arch (amd64/arm64)
          sur GHCR ; Flux prend le relais côté cluster.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Job</th>
                <th className="px-3 py-2 text-left">Trigger</th>
                <th className="px-3 py-2 text-left">Action</th>
              </tr>
            </thead>
            <tbody>
              {CI_JOBS.map((j) => (
                <tr key={j.name} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{j.name}</td>
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{j.trigger}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{j.what}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Pipelines de données</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {PIPELINES.map((p) => (
            <div key={p.title} className="rounded-lg border bg-card p-4 shadow-sm">
              <h3 className="font-medium text-sm mb-2">{p.title}</h3>
              <ol className="text-xs text-muted-foreground space-y-1 list-decimal pl-4">
                {p.steps.map((s, i) => (
                  <li key={i}>{s}</li>
                ))}
              </ol>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
