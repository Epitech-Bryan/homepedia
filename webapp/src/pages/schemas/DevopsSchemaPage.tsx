/**
 * DevOps / CI/CD reference. Reflects the live compose.yml +
 * .github/workflows/*.yml + backend & webapp Dockerfiles. Update when one
 * of those moves.
 */

const COMPOSE_SERVICES = [
  { name: "traefik", img: "traefik:v3.3", ports: "80, 8090", role: "Reverse proxy + dashboard" },
  { name: "webapp", img: "node:20 dev", ports: "5173", role: "Vite dev server" },
  {
    name: "backend",
    img: "spring-boot",
    ports: "8080 (via traefik)",
    role: "REST API",
  },
  { name: "db", img: "postgres:16-alpine", ports: "5432", role: "Postgres principal" },
  { name: "mongo", img: "mongo:7", ports: "27017", role: "Reviews" },
  { name: "redis", img: "redis:7-alpine", ports: "6379", role: "Cache @Cacheable" },
  {
    name: "spark-master",
    img: "bitnami/spark:3.5",
    ports: "7077, 8088",
    role: "Spark master + UI",
  },
  { name: "spark-worker", img: "bitnami/spark:3.5", ports: "—", role: "2 cores / 2 GB" },
];

const CI_JOBS = [
  { name: "lint-webapp", trigger: "webapp/**", what: "pnpm lint + format:check" },
  { name: "test-webapp", trigger: "webapp/**", what: "Vitest unit + jsdom" },
  {
    name: "build-webapp",
    trigger: "webapp/**",
    what: "Vite build (vérifie aussi tsc -b)",
  },
  { name: "lint-backend", trigger: "backend/**", what: "Spotless check" },
  {
    name: "test-backend",
    trigger: "backend/**",
    what: "mvn verify -Pintegration (PG + Mongo + Redis Testcontainers)",
  },
  { name: "build-backend", trigger: "backend/**", what: "mvn package, jar runtime" },
  { name: "docker", trigger: "tag v*", what: "Build + push images to GHCR" },
];

const PIPELINES = [
  {
    title: "DVF (Demandes Valeurs Foncières)",
    steps: [
      "Téléchargement gzip data.gouv",
      "GZIPInputStream → COPY vers transactions_<year>_new (shadow)",
      "ATTACH PARTITION atomique (sub-second)",
      "ANALYZE transactions_<year>",
      "CityDvfStatsAggregator.refreshYear",
      "CityQuarterlyPriceAggregator.refreshYear",
    ],
  },
  {
    title: "INSEE / Filosofi (IRIS)",
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
];

export function DevopsSchemaPage() {
  return (
    <div className="space-y-10 pb-8">
      <section>
        <h2 className="text-lg font-semibold mb-3">Stack docker compose</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Stack de dev local. Traefik route <code>homepedia.localhost</code> sur le webapp et{" "}
          <code>/api</code> sur le backend.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Service</th>
                <th className="px-3 py-2 text-left">Image</th>
                <th className="px-3 py-2 text-left">Ports</th>
                <th className="px-3 py-2 text-left">Rôle</th>
              </tr>
            </thead>
            <tbody>
              {COMPOSE_SERVICES.map((s) => (
                <tr key={s.name} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{s.name}</td>
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{s.img}</td>
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{s.ports}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{s.role}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">CI/CD — GitHub Actions</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Jobs scopés via <code>paths</code> sur les fichiers changés — un PR webapp ne déclenche
          pas les tests backend, et vice-versa.
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
        <h2 className="text-lg font-semibold mb-3">Pipeline déploiement</h2>
        <div className="font-mono text-[11px] leading-5 border rounded-lg bg-muted/30 p-4 overflow-x-auto">
          <pre className="whitespace-pre">{`  PR sur main
       │
       ▼
  ┌──────────┐    ┌──────────┐    ┌──────────┐
  │  lint    │ ─► │  test    │ ─► │  build   │
  │  spotless│    │  -Pinteg │    │  jar     │
  └──────────┘    └──────────┘    └──────────┘
                                       │
                                       ▼
                                  ┌──────────┐
                                  │  merge   │
                                  └────┬─────┘
                                       │  tag v*
                                       ▼
                                  ┌──────────┐    ┌──────────┐
                                  │  Docker  │ ─► │  GHCR    │
                                  │  build   │    │  push    │
                                  └──────────┘    └──────────┘
                                                       │
                                                       ▼
                                                  ┌──────────┐
                                                  │   K8s    │
                                                  │  Flux/   │
                                                  │  Helm    │
                                                  └──────────┘`}</pre>
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
