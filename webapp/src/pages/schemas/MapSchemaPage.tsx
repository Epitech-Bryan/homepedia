import { ReactFlow, Background, Controls, MiniMap, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { nodeTypes } from "./diagram-nodes";

/**
 * Carte — comment la map France/monde est construite : pipeline MVT
 * multi-layer, choroplèthe par quantiles, drilldown arrondissements,
 * stitching antiméridien pour la Russie. Tout ce qui sort de l'écran
 * passe par le pod rest-api ; aucun service externe de tiles.
 */

const MAP_NODES: Node[] = [
  {
    id: "src-france",
    type: "schema",
    position: { x: 0, y: 0 },
    data: {
      kind: "edge",
      title: "geo.api.gouv.fr",
      subtitle: "Régions · Dépts · Communes",
      hint: "+ arrondissements municipaux (Paris/Lyon/Marseille)",
      ports: { right: true, bottom: true },
    },
  },
  {
    id: "src-world",
    type: "schema",
    position: { x: 0, y: 140 },
    data: {
      kind: "edge",
      title: "GADM 4.1",
      subtitle: "admin-1 monde",
      hint: "30+ pays, Wikidata P150 → population",
      ports: { right: true, top: true, bottom: true },
    },
  },
  {
    id: "src-be",
    type: "schema",
    position: { x: 0, y: 280 },
    data: {
      kind: "edge",
      title: "Statbel",
      subtitle: "Belgium",
      hint: "11 provinces + 581 communes",
      ports: { right: true, top: true },
    },
  },

  {
    id: "tile-builder",
    type: "schema",
    position: { x: 340, y: 70 },
    data: {
      kind: "job",
      title: "CityTileBuilder",
      subtitle: "@PostConstruct → @Async @EventListener",
      hint: "GZIP stream · join stats · 3 sources GeoJSON",
      ports: { left: true, right: true, bottom: true },
    },
  },
  {
    id: "stats",
    type: "schema",
    position: { x: 340, y: 220 },
    data: {
      kind: "data",
      title: "city_dvf_yearly_stats",
      subtitle: "+ city_tile_stats",
      hint: "transactions · avg €/m² · avg price",
      ports: { top: true, right: true },
    },
  },
  {
    id: "ranges-disk",
    type: "schema",
    position: { x: 340, y: 360 },
    data: {
      kind: "data",
      title: "metric-ranges.json",
      subtitle: "/data/tiles/",
      hint: "Persisté à chaque rebuild · quantiles bornes",
      ports: { top: true, right: true },
    },
  },

  {
    id: "tippecanoe",
    type: "schema",
    position: { x: 720, y: 70 },
    data: {
      kind: "job",
      title: "tippecanoe (Felt 2.62)",
      subtitle: "3 layers en 1 mbtiles",
      hint: "regions z4-6 · depts z7-8 · cities+arr z9-14",
      ports: { left: true, right: true },
    },
  },
  {
    id: "mbtiles",
    type: "schema",
    position: { x: 720, y: 220 },
    data: {
      kind: "data",
      title: "cities.mbtiles",
      subtitle: "SQLite-backed MVT",
      hint: "~234 MB · arrondissements inclus",
      ports: { left: true, right: true },
    },
  },

  {
    id: "tile-ctl",
    type: "schema",
    position: { x: 1060, y: 70 },
    data: {
      kind: "service",
      title: "TileController",
      subtitle: "/api/tiles/cities/{z}/{x}/{y}.pbf",
      hint: "Tile vide → 204 No Content · cache 30j",
      ports: { left: true, right: true, bottom: true },
    },
  },
  {
    id: "range-ctl",
    type: "schema",
    position: { x: 1060, y: 220 },
    data: {
      kind: "service",
      title: "/metric-ranges",
      subtitle: "Quantile breaks",
      hint: "6 break-points → 7 buckets choro",
      ports: { left: true, right: true },
    },
  },

  {
    id: "vector-grid",
    type: "schema",
    position: { x: 1380, y: 70 },
    data: {
      kind: "service",
      title: "CityVectorGridLayer",
      subtitle: "leaflet.vectorgrid",
      hint: "styleFor par layer · minNativeZoom 4 · max 14",
      ports: { left: true, bottom: true },
    },
  },
  {
    id: "backdrop",
    type: "schema",
    position: { x: 1380, y: 220 },
    data: {
      kind: "service",
      title: "BackdropCountriesLayer",
      subtitle: "SVG · zoom-aware weight",
      hint: "Natural Earth · stitchAntimeridian()",
      ports: { left: true, top: true },
    },
  },
];

const MAP_EDGES: Edge[] = [
  {
    id: "m1",
    source: "src-france",
    target: "tile-builder",
    sourceHandle: "right",
    targetHandle: "left",
    label: "GeoJSON",
    animated: true,
  },
  {
    id: "m2",
    source: "src-world",
    target: "backdrop",
    sourceHandle: "right",
    targetHandle: "left",
    label: "world-admin1",
  },
  {
    id: "m3",
    source: "src-be",
    target: "backdrop",
    sourceHandle: "right",
    targetHandle: "left",
    label: "communes BE",
  },
  { id: "m4", source: "stats", target: "tile-builder", label: "JOIN insee" },
  { id: "m5", source: "tile-builder", target: "ranges-disk", label: "write quantiles" },
  {
    id: "m6",
    source: "tile-builder",
    target: "tippecanoe",
    sourceHandle: "right",
    targetHandle: "left",
    label: "spawn",
    animated: true,
  },
  {
    id: "m7",
    source: "tippecanoe",
    target: "mbtiles",
    label: "write",
  },
  {
    id: "m8",
    source: "mbtiles",
    target: "tile-ctl",
    sourceHandle: "right",
    targetHandle: "left",
    label: "lookup",
  },
  {
    id: "m9",
    source: "ranges-disk",
    target: "range-ctl",
    sourceHandle: "right",
    targetHandle: "left",
    label: "load @startup",
  },
  {
    id: "m10",
    source: "tile-ctl",
    target: "vector-grid",
    sourceHandle: "right",
    targetHandle: "left",
    label: ".pbf",
    animated: true,
  },
  {
    id: "m11",
    source: "range-ctl",
    target: "vector-grid",
    sourceHandle: "right",
    targetHandle: "left",
    label: "breaks",
  },
];

const LAYERS = [
  {
    layer: "regions",
    zoom: "4 → 6",
    features: "13",
    src: "geo.api.gouv.fr/regions",
    simplif: "12",
    note: "Niveau vue France entière · payload < 60 kB",
  },
  {
    layer: "departments",
    zoom: "7 → 8",
    features: "101",
    src: "geo.api.gouv.fr/departements",
    simplif: "10",
    note: "Bascule auto à z=7 quand on entre dans la France",
  },
  {
    layer: "cities",
    zoom: "9 → 14",
    features: "34 968 + 45 arr.",
    src: "geo.api.gouv.fr/communes + ?type=arrondissement-municipal",
    simplif: "8",
    note: "Paris/Lyon/Marseille remplacées par leurs arrondissements à z≥12",
  },
];

const STATS_BAKED = [
  {
    prop: "transactions",
    type: "int",
    src: "city_dvf_yearly_stats.transaction_count",
    note: "Nombre brut sur la dernière année DVF",
  },
  {
    prop: "avg_price",
    type: "double",
    src: "AVG(property_value) dédup mutation_id",
    note: "Prix moyen, toutes typologies confondues",
  },
  {
    prop: "avg_price_per_sqm",
    type: "double",
    src: "SUM(price)/SUM(surface) pondéré",
    note: "Pondération par surface — pas une moyenne d'AVG",
  },
  {
    prop: "population",
    type: "int",
    src: "cities.population (INSEE)",
    note: "Pour les arrondissements : population de l'arr, pas de la commune mère",
  },
  {
    prop: "metric_quantile",
    type: "byte 0..6",
    src: "Bucket pré-calculé par quantile",
    note: "Évite un bisect côté browser sur chaque feature",
  },
];

const CHORO_STEPS = [
  {
    step: "1. Distribution",
    detail: "Toutes les valeurs non-nulles de la métrique sont collectées",
    why: "Inclut Paris (2M hab) ET un village (300 hab) sans biaiser un côté",
  },
  {
    step: "2. Tri ascendant",
    detail: "On trie la liste",
    why: "Préalable au calcul des quantiles",
  },
  {
    step: "3. 6 break-points",
    detail: "Indices au 14, 28, 42, 57, 71, 85 e percentile",
    why: "7 buckets équilibrés (≈14% de la population dans chacun)",
  },
  {
    step: "4. Bake dans MVT",
    detail: "metric_quantile = 0..6 écrit dans la feature property",
    why: "Le browser fait juste un lookup couleur, pas de calcul à chaque tile",
  },
  {
    step: "5. Persist disque",
    detail: "metric-ranges.json sur PVC /data/tiles",
    why: "Survive aux rolling restarts (sinon Map blanche pendant le rebuild)",
  },
  {
    step: "6. Reload @startup",
    detail: "@PostConstruct loadPersistedRanges()",
    why: "API /metric-ranges disponible immédiatement, pas après 17 min",
  },
];

const QUIRKS = [
  {
    quirk: "Russie coupée en deux",
    cause: "Natural Earth livre la Tchoukotka en longitudes négatives (< −170°)",
    fix: "stitchAntimeridian() · ring avec ≥1 sommet < −150° → +360°",
  },
  {
    quirk: "Paris washing out le gradient",
    cause: "Linéaire sur [min..max] : Paris à 2.1M → tout le reste sous 5%",
    fix: "Quantile binning (7 buckets équilibrés)",
  },
  {
    quirk: "Tile z=8 404 storm",
    cause: "Leaflet pré-fetch z=8 pendant le zoom transition, tippecanoe ne génère pas",
    fix: "minNativeZoom: 4 sur la layer + 204 No Content côté API",
  },
  {
    quirk: "geo.api.gouv.fr arrondissements 404",
    cause: "L'endpoint /communes/{code}/arrondissements-municipaux a été retiré en 2024",
    fix: "/communes?codeDepartement={d}&type=arrondissement-municipal",
  },
  {
    quirk: "Hover bloqué au zoom 9",
    cause: "Departments + cities se chevauchent à z=10, 2 layers sous le curseur",
    fix: "Zones disjointes : depts z7-8, cities z9-14",
  },
  {
    quirk: "Map blanche après pod restart",
    cause: "metricRanges = Map.of() reset en mémoire",
    fix: "Persist + reload + fallback recompute from DB si fichier manquant",
  },
];

const FRONT_LAYERS = [
  {
    component: "PersistentMap",
    role: "Container Leaflet · gère zoom / pan persistants",
    detail: "ZOOM_THRESHOLD 5/7/10/12 → bascule des layers actifs",
  },
  {
    component: "CityVectorGridLayer",
    role: "MVT layer principal · stats baked",
    detail: "Une seule layer Leaflet, 3 styleFor (regions/departments/cities)",
  },
  {
    component: "BackdropCountriesLayer",
    role: "Pays du monde en SVG derrière",
    detail: "weight zoom-aware (0.5 → 2.5) · décliné par hover",
  },
  {
    component: "useGeoBelgiumMunicipalities",
    role: "Hook GeoJSON BE communes",
    detail: "Activé à z≥10 quand la bbox croise la Belgique",
  },
  {
    component: "leaflet-vectorgrid-setup",
    role: "Monkey-patch fetch errors",
    detail: "_getVectorTilePromise.catch → layers vides plutôt que crash",
  },
];

export function MapSchemaPage() {
  return (
    <div className="space-y-10 pb-8">
      <section>
        <h2 className="text-lg font-semibold mb-2">Pipeline carte de bout en bout</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Trois sources GeoJSON entrent dans <code>CityTileBuilder</code>, qui les join avec les
          stats Postgres, écrit les bornes de quantiles sur disque, puis spawn{" "}
          <code>tippecanoe</code> pour produire un seul <code>cities.mbtiles</code> à trois layers.
          Le browser fait juste un lookup couleur — aucun calcul stats côté client.
        </p>
        <div className="h-[560px] rounded-lg border bg-muted/20">
          <ReactFlow
            nodes={MAP_NODES}
            edges={MAP_EDGES}
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
        <h2 className="text-lg font-semibold mb-3">Layers MVT &amp; bandes de zoom</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Le mbtiles contient trois layers nommés, chacun sur une plage de zoom disjointe — pas de
          chevauchement, donc pas de double rendu / double event hover.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Layer MVT</th>
                <th className="px-3 py-2 text-right">Zoom</th>
                <th className="px-3 py-2 text-right">Features</th>
                <th className="px-3 py-2 text-left">Source</th>
                <th className="px-3 py-2 text-right">Simplif.</th>
                <th className="px-3 py-2 text-left">Note</th>
              </tr>
            </thead>
            <tbody>
              {LAYERS.map((l) => (
                <tr key={l.layer} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{l.layer}</td>
                  <td className="px-3 py-2 font-mono text-xs text-right">{l.zoom}</td>
                  <td className="px-3 py-2 font-mono text-xs text-right">{l.features}</td>
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{l.src}</td>
                  <td className="px-3 py-2 font-mono text-xs text-right">{l.simplif}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{l.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Properties bakées dans chaque feature</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Plutôt que d'appeler <code>/api/stats/cities?codes=…</code> (10s à froid avant), les stats
          sont écrites directement dans le MVT au moment du build. Lookup constant, sans round-trip.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Property</th>
                <th className="px-3 py-2 text-left">Type</th>
                <th className="px-3 py-2 text-left">Source</th>
                <th className="px-3 py-2 text-left">Note</th>
              </tr>
            </thead>
            <tbody>
              {STATS_BAKED.map((s) => (
                <tr key={s.prop} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{s.prop}</td>
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{s.type}</td>
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{s.src}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{s.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Calcul du choroplèthe (quantiles)</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Échelle linéaire abandonnée : avec Paris à 2.1M habitants, toutes les autres communes
          tombaient dans le premier bucket et la carte devenait beige uniforme. Le quantile binning
          découpe la distribution en 7 tranches d'effectifs égaux.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Étape</th>
                <th className="px-3 py-2 text-left">Détail</th>
                <th className="px-3 py-2 text-left">Pourquoi</th>
              </tr>
            </thead>
            <tbody>
              {CHORO_STEPS.map((c) => (
                <tr key={c.step} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{c.step}</td>
                  <td className="px-3 py-2 text-xs">{c.detail}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{c.why}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Stack frontend</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Les composants côté webapp et leur responsabilité. Une seule layer Leaflet sert régions +
          départements + communes ; les couches mondiales restent en SVG (volumes modestes, pas la
          peine de baker en MVT).
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Composant</th>
                <th className="px-3 py-2 text-left">Rôle</th>
                <th className="px-3 py-2 text-left">Détail</th>
              </tr>
            </thead>
            <tbody>
              {FRONT_LAYERS.map((f) => (
                <tr key={f.component} className="border-t">
                  <td className="px-3 py-2 font-mono text-xs">{f.component}</td>
                  <td className="px-3 py-2 text-xs">{f.role}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{f.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Pièges &amp; correctifs notables</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Ce qui a explosé en chemin et comment c'est corrigé aujourd'hui. Conservé pour mémoire,
          parce que ces bugs reviendraient en cas de régression sur les fichiers concernés.
        </p>
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">Symptôme</th>
                <th className="px-3 py-2 text-left">Cause</th>
                <th className="px-3 py-2 text-left">Correctif en place</th>
              </tr>
            </thead>
            <tbody>
              {QUIRKS.map((q) => (
                <tr key={q.quirk} className="border-t">
                  <td className="px-3 py-2 text-xs font-medium">{q.quirk}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{q.cause}</td>
                  <td className="px-3 py-2 text-xs">{q.fix}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
