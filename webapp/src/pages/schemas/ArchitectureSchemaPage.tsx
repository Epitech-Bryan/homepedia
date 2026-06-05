import { ReactFlow, Background, Controls, MiniMap, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { nodeTypes } from "./diagram-nodes";

/**
 * Interactive system topology rendered with React Flow. Each box is a real
 * Kubernetes workload — namespace, pod and storage shapes line up with what
 * runs on the FerrLabs cluster today. Pan + zoom + minimap keep the view
 * usable on every screen size; the hand-built ASCII version this replaced
 * was unreadable below ~1200 px wide.
 */
const NODES: Node[] = [
  {
    id: "ns-traefik",
    type: "group",
    position: { x: 60, y: 0 },
    data: { label: "namespace · traefik", width: 240, height: 110 },
    draggable: false,
    selectable: false,
  },
  {
    id: "ns-homepedia",
    type: "group",
    position: { x: 360, y: 0 },
    data: { label: "namespace · homepedia", width: 460, height: 380 },
    draggable: false,
    selectable: false,
  },
  {
    id: "ns-stores",
    type: "group",
    position: { x: 880, y: 0 },
    data: { label: "shared infra namespaces", width: 260, height: 380 },
    draggable: false,
    selectable: false,
  },

  {
    id: "browser",
    type: "schema",
    position: { x: 60, y: -130 },
    data: {
      kind: "edge",
      title: "Browser",
      subtitle: "homepedia.bryan-ferrando.fr",
      hint: "React 19 SPA, ~250 KB gzip after split",
    },
  },
  {
    id: "traefik",
    type: "schema",
    position: { x: 100, y: 40 },
    data: {
      kind: "infra",
      title: "Traefik v3",
      subtitle: "IngressRoute",
      hint: "Let's Encrypt via cert-manager, gzip + br",
      ports: { top: true, bottom: true, right: true },
    },
  },

  {
    id: "webapp",
    type: "schema",
    position: { x: 380, y: 60 },
    data: {
      kind: "service",
      title: "homepedia-webapp",
      subtitle: "Deployment · nginx:alpine",
      hint: "Static SPA, baked Vite bundle",
      ports: { top: true, bottom: false, left: true },
    },
  },
  {
    id: "rest",
    type: "schema",
    position: { x: 590, y: 60 },
    data: {
      kind: "service",
      title: "homepedia-rest-api",
      subtitle: "Deployment · Spring Boot 3.5 / Java 21",
      hint: "JPA + Mongo + Redis + Vault sidecar",
      ports: { top: true, bottom: true, left: true, right: false },
    },
  },
  {
    id: "tile-builder",
    type: "schema",
    position: { x: 380, y: 200 },
    data: {
      kind: "job",
      title: "City + World TileBuilder",
      subtitle: "@Async @EventListener",
      hint: "Tippecanoe → cities.mbtiles + world.mbtiles on /data PVC",
      ports: { top: true, bottom: true, right: true },
    },
  },
  {
    id: "mongo",
    type: "schema",
    position: { x: 600, y: 220 },
    data: {
      kind: "data",
      title: "MongoDB 7",
      subtitle: "StatefulSet · /data PVC",
      hint: "city_reviews · text + sentiment",
      ports: { top: true, bottom: false },
    },
  },

  {
    id: "pg",
    type: "schema",
    position: { x: 900, y: 60 },
    data: {
      kind: "data",
      title: "TimescaleDB (CNPG)",
      subtitle: "ns · postgres",
      hint: "transactions partitioned by year, BRIN + pg_trgm",
      ports: { top: true, bottom: false, left: true },
    },
  },
  {
    id: "redis",
    type: "schema",
    position: { x: 900, y: 200 },
    data: {
      kind: "data",
      title: "Redis 7",
      subtitle: "ns · redis",
      hint: "@Cacheable, prefix homepedia:v2:*",
      ports: { top: true, left: true, bottom: false },
    },
  },
  {
    id: "vault",
    type: "schema",
    position: { x: 900, y: 320 },
    data: {
      kind: "infra",
      title: "Vault",
      subtitle: "ns · vault, agent-injector",
      hint: "DB creds, registry pull, OAuth tokens",
      ports: { top: true, bottom: false, left: true },
    },
  },
];

const EDGES: Edge[] = [
  { id: "e1", source: "browser", target: "traefik", animated: true, label: "HTTPS" },
  {
    id: "e2",
    source: "traefik",
    target: "webapp",
    sourceHandle: "right",
    targetHandle: "left",
    label: "/",
  },
  {
    id: "e3",
    source: "traefik",
    target: "rest",
    sourceHandle: "right",
    targetHandle: "left",
    label: "/api",
  },
  {
    id: "e4",
    source: "rest",
    target: "pg",
    sourceHandle: "right",
    targetHandle: "left",
    label: "JPA / Hikari",
  },
  {
    id: "e5",
    source: "rest",
    target: "redis",
    sourceHandle: "right",
    targetHandle: "left",
    label: "cache",
  },
  {
    id: "e6",
    source: "rest",
    target: "mongo",
    label: "reviews",
  },
  {
    id: "e7",
    source: "rest",
    target: "tile-builder",
    label: "after DVF",
    style: { strokeDasharray: "4 4" },
  },
  {
    id: "e8",
    source: "tile-builder",
    target: "pg",
    sourceHandle: "right",
    targetHandle: "left",
    label: "city stats",
    style: { strokeDasharray: "4 4" },
  },
  {
    id: "e9",
    source: "vault",
    target: "rest",
    sourceHandle: "left",
    targetHandle: "left",
    label: "agent-inject",
    style: { strokeDasharray: "2 2" },
  },
];

const COMPONENTS = [
  {
    title: "Webapp (React 19 + Vite)",
    details: [
      "Leaflet + VectorGrid : CityVectorGridLayer (FR) + WorldVectorGridLayer (monde)",
      "TanStack Query — cache + revalidation",
      "Tailwind 4 + shadcn/ui + base-ui",
      "Routing : react-router-dom v7",
      "React Flow pour les schémas",
    ],
  },
  {
    title: "REST API (Spring Boot 3.5 / Java 21)",
    details: [
      "JPA + Hibernate sur Postgres partitionné",
      "Spring Data Mongo pour les reviews",
      "Spring Cache → Redis (Jackson, prefix versionné)",
      "Liquibase, 17 changesets",
      "CityTileBuilder : Tippecanoe en sous-process",
      "Resilience4j sur INSEE / ADEME / data.gouv",
    ],
  },
  {
    title: "PostgreSQL 16 (CNPG)",
    details: [
      "Opérateur CloudNativePG, 1 primary + 1 replica",
      "transactions partitionnée par année (2014..2030)",
      "pg_trgm GIN pour l'autocomplete villes",
      "BRIN sur mutation_date",
      "Pre-agg : city_dvf_yearly_stats + quarterly",
    ],
  },
  {
    title: "MongoDB 7",
    details: [
      "StatefulSet 1 replica, Longhorn 5 Gi",
      "city_reviews : free text + sentiment + rating",
      "@TextIndexed FR avec stemming",
    ],
  },
  {
    title: "Redis 7",
    details: [
      "Préfixe versionné (homepedia:v2:…)",
      "Flush stale au boot",
      "4 caches : geo, refdata, stats, reviews",
    ],
  },
  {
    title: "Vector Tiles",
    details: [
      "Tippecanoe 2.62.0 bundled dans l'image",
      "FR : geo.api.gouv.fr → cities.mbtiles avec stats baked",
      "Monde : GADM → world.mbtiles 4 layers (countries/admin1/2/3)",
      "/api/tiles/cities + /api/tiles/world/{z}/{x}/{y}.pbf",
      "TileBuildLock sérialise les deux builds · hot-reload après rebuild",
    ],
  },
];

export function ArchitectureSchemaPage() {
  return (
    <div className="space-y-8 pb-8">
      <section>
        <h2 className="text-lg font-semibold mb-2">Vue d'ensemble</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Trafic utilisateur → Traefik (IngressRoute) → SPA React ou REST API Spring Boot. Le
          backend lit Postgres (DVF partitionné), Mongo (reviews) et Redis (cache). Vault injecte
          les secrets via sidecar ; Tippecanoe tourne dans le pod rest-api après chaque import DVF.
        </p>

        <div className="h-[480px] rounded-lg border bg-muted/20">
          <ReactFlow
            nodes={NODES}
            edges={EDGES}
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
        <h2 className="text-lg font-semibold mb-3">Composants</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {COMPONENTS.map((c) => (
            <div key={c.title} className="rounded-lg border bg-card p-4 shadow-sm">
              <h3 className="font-medium text-sm mb-2">{c.title}</h3>
              <ul className="text-xs text-muted-foreground space-y-1 list-disc pl-4">
                {c.details.map((d) => (
                  <li key={d}>{d}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
