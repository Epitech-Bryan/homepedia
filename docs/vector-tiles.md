# Vector tiles pipeline

Background for the `/tiles/cities/{z}/{x}/{y}.pbf` endpoint (`VectorTileService` + `TileController`). The endpoint reads a pre-generated `cities.mbtiles` SQLite file. This document covers how to produce that file and how to ship it to the cluster.

## What the endpoint expects

A standard mbtiles file at the path configured by `homepedia.tiles.cities-path` (default `/data/tiles/cities.mbtiles`):

- `tiles(zoom_level, tile_column, tile_row, tile_data BLOB)` — TMS y-coordinates, gzipped PBF blobs
- `metadata(name, value)` — at least `format=pbf`, `bounds=-5,41,10,51` (France), `minzoom`, `maxzoom`

Tippecanoe writes this layout natively.

## Generating the file

```bash
# 1. Source GeoJSON — the same one geo.api.gouv.fr returns, fetched once
#    per department and concatenated into a single FeatureCollection.
#    Adjust if a higher-resolution source becomes available (IGN ADMIN-EXPRESS).
for dept in 01 02 ... 974 976; do
  curl -s "https://geo.api.gouv.fr/departements/${dept}/communes?fields=nom,code,population,surface&format=geojson&geometry=contour" \
    > "/tmp/communes-${dept}.geojson"
done

# Merge with mapshaper or jq into one file.
jq -s '{type:"FeatureCollection", features: map(.features) | add}' \
  /tmp/communes-*.geojson > /tmp/communes-fr.geojson

# 2. Tippecanoe: zoom 9..14, Douglas-Peucker at z9-z11 to keep tiles small,
#    full geometry at z12+ for the click hit-test to feel accurate.
tippecanoe \
  --output /tmp/cities.mbtiles \
  --layer cities \
  --minimum-zoom=9 \
  --maximum-zoom=14 \
  --drop-densest-as-needed \
  --extend-zooms-if-still-dropping \
  --simplification=10 \
  --no-tile-size-limit \
  /tmp/communes-fr.geojson
```

Expected output: ~30-50 MB mbtiles file covering metropolitan France + DROM.

## Shipping to the cluster

Two reasonable approaches:

**Option A — bundle in the image** (simplest, no extra k8s object):

- Copy `cities.mbtiles` into `backend/rest-api/src/main/resources/data/`.
- Set `homepedia.tiles.cities-path=classpath:/data/cities.mbtiles` (requires extracting to a temp file at boot since JDBC can't read from a JAR).
- Image size grows by ~40 MB. Manageable but every commune update means a release.

**Option B — PersistentVolume + ConfigMap path** (recommended for ops):

- Add a 200 MB `PersistentVolumeClaim` to the homepedia namespace, name `tiles-data`.
- Mount it on `/data/tiles` in the rest-api Deployment.
- A one-shot `Job` mounts the same PVC and runs the tippecanoe pipeline from a Debian image — or copy the file in via `kubectl cp` for the initial bootstrap.
- The rest-api image stays small; refreshing the tiles is a Job re-run, not a redeploy.

Add the PVC + mount under `/home/bryan/k8s/homepedia/`, alongside the existing HelmReleases.

## Wiring the frontend

Step 3 of issue #6. Once the file is in place and the endpoint returns 200 on a known tile (e.g. `curl https://homepedia.bryan-ferrando.fr/api/tiles/cities/10/512/352.pbf | wc -c` > 0), swap `useGeoCitiesForDepartments` for `L.vectorGrid.protobuf` in `PersistentMap.tsx`. The choropleth callback in `FranceMap.tsx` already keys on `feature.properties.code` so the styling layer reuses the existing `metricByCode` map.

Keep the geo.api.gouv.fr code-path behind a `VITE_USE_GEO_API_FALLBACK=true` flag for one release so a bad tile bundle is recoverable without a frontend rollback.

### Loading Leaflet.VectorGrid under Vite

The plugin (`leaflet.vectorgrid@1.3.0`) is pre-ESM and reads `L` as a free global at module-evaluation time. Under Vite's chunked ESM graph two precautions are needed, both already in place but worth documenting because the failure mode is silent (layer mounts, no error, no tiles fetched):

1. **Import through `src/lib/leaflet-vectorgrid-setup.ts`** — that module side-effect-imports `./leaflet-global` first (which publishes `globalThis.L = L`), then imports `leaflet.vectorgrid` so the plugin's `L.vectorGrid = …` lands on the same Leaflet instance MapContainer pulls in. Importing `"leaflet.vectorgrid"` directly from a component breaks the order and the plugin attaches to a sibling copy of Leaflet.
2. **Keep the plugin in `vendor-leaflet`** — `vite.config.ts` lists `leaflet.vectorgrid` in the `vendor-leaflet` manual chunk so the plugin and Leaflet share a module record. Without that, Rollup splits them and the plugin's bundle gets its own dead-end `L`.

Both are unit-test-invisible — they only surface in a real browser. If a future Leaflet plugin shows the same silent symptom, route it through the same setup module.

## Validating in CI

`LiquibaseMigrationIT` already validates PG; the equivalent for tiles is a unit test that constructs a tiny mbtiles file with one row in a temp directory, points `VectorTileService` at it, and asserts the controller returns the bytes. Tracked in issue #1's testcontainers scope.
