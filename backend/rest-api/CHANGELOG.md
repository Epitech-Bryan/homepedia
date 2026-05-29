# [1.2.0](https://gitlab.com/t-dat-902/homepedia/compare/rest-api-v1.1.1...rest-api-v1.2.0) (2026-04-24)


### Features

* **batch:** adapt importers for real open data sources ([65aec9b](https://gitlab.com/t-dat-902/homepedia/commit/65aec9b1f0f16329e2b17f488b6121cd33640993))

## [3.74.2] - 2026-05-29

### Bug Fixes

- fix(db): self-heal dept_dvf_stats column types on startup before schema validation

## [3.74.1] - 2026-05-29

### Bug Fixes

- fix(spark): truncate dept_dvf_stats on overwrite to preserve column types

## [3.74.0] - 2026-05-29

### Features

- feat(country): import current World Bank metrics for all countries and overlay onto geo data

## [3.73.0] - 2026-05-29

### Features

- feat(map): bake complete country metrics (population, gdp, gdpPerCapita, area) into world tiles
- feat(map): mirror 2D zoom bands on the globe (regions to departments to communes)
- feat(map): render real centroid bubbles on the globe in bubbles mode
- feat(map): add OSM POI layer, hover-address and bubbles fallback to the globe
- feat(map): port heatmap and transaction pins to the globe and cap world tiles for perf
- feat(map): add opt-in MapLibre globe view with choropleth alongside the 2D map
- feat(map): allow dezooming to the full planet view (minZoom 2 to 1)

### Bug Fixes

- fix(map): use lightweight countries layer at planet zoom and color it via metricByCode
- fix(map): align globe zone layers to the tippecanoe zoom bands and add countries/admin3

## [3.72.0] - 2026-05-28

### Features

- feat(spark): make the dvf aggregate output table configurable via --output-table

## [3.71.0] - 2026-05-28

### Features

- feat(spark): run dvf aggregation as a kubernetes job reading the transactions table
- feat(spark): optional LSH nearest-neighbour matcher for comparable sales

### Bug Fixes

- perf(spark): stage geo-dvf to partitioned parquet and read it columnar in dvf aggregate

## [3.70.0] - 2026-05-28

### Features

- feat(dvf): import geo-dvf per-mutation coordinates for precise heatmap and markers

### Bug Fixes

- perf(heatpoints): finer 44m grid now that transactions are geocoded
- fix(transactions): tolerate null property_type and date in marker rows
- fix(map): guard FitBounds against non-finite bounds and make heat kernel zoom-aware

## [3.69.0] - 2026-05-28

### Features

- feat(admin): surface live import phase in job status cards

## [3.68.0] - 2026-05-28

### Features

- feat(map): add pollution choropleth metric (GES 1-7)
- feat(indicators): persist GES class from DPE feed and expose city pollution score

### Bug Fixes

- fix(stats): move GES lateral comments out of native query string
- fix(indicators): use DpeRawRecord#dpeLabelGes accessor for GES aggregation

## [3.67.1] - 2026-05-24

### Bug Fixes

- perf(reviews): stream generation into bounded queue with parallel mongo writers
- fix(tiles): pull regions/departements geojson from github after geo.api.gouv.fr regression
- fix(map): isolate vector tile panes, gate layers by zoom band, clear stale canvas
- fix(webapp): rewrite /api prefix in vite dev proxy
- fix(world): skip MVT countries draw so SVG choropleth shows at z<5

## [3.67.0] - 2026-05-23

### Features

- feat(pois): backend proxy + Redis 7d cache in front of Overpass

## [3.66.0] - 2026-05-23

### Features

- feat(map): satellite basemap + OSM POIs + hover reverse-geocode + admin-3 expansion
- feat(map): overlay OSM street/POI labels at z>=9 for non-FR detail

### Bug Fixes

- fix(map): mount world MVT layer below city so clicks at z=9+ work

## [3.65.0] - 2026-05-23

### Features

- feat(world): admin-1 detail page + global search + admin-3 MVT layer

## [3.64.0] - 2026-05-23

### Features

- feat(world): admin-1 round 4 — +50 missing countries + GDP backfill

## [3.63.0] - 2026-05-23

### Features

- feat(world): admin-2 for USA/BRA/MEX/CHN/IND + ~50 missing countries

### Bug Fixes

- fix(ui): format Select trigger labels instead of leaking raw values

## [3.62.1] - 2026-05-23

### Bug Fixes

- fix(dpe): skip rows whose insee code exceeds the varchar(9) column

## [3.62.0] - 2026-05-23

### Features

- feat(admin): expose ExitStatus message under FAILED jobs

## [3.61.0] - 2026-05-23

### Features

- feat(world): MVT pipeline for countries + admin-1 + admin-2

## [3.60.0] - 2026-05-23

### Features

- feat(world): +30 admin-1 countries (Africa/MENA/Central America/Pacific)
- feat(admin): add rebuild tiles button + auto evict geo cache

## [3.59.0] - 2026-05-23

### Features

- feat(world): admin-2 layer for European countries past zoom 7

## [3.58.0] - 2026-05-23

### Features

- feat(world): +25 admin-1 countries + bake area and gdp metrics
- feat(reviews): loading checkpoints + skeleton placeholders

### Bug Fixes

- perf(spark): COPY FROM STDIN + drop/recreate indexes + PK pre-sort

## [3.57.0] - 2026-05-23

### Features

- feat(tiles): bake IRIS layer for sub-commune drilldown at z=13-14

### Bug Fixes

- fix(map): create backdrop pane synchronously to avoid _removePath crash
- perf(webapp): useDeferredValue on bounds, unbundle recharts, SW tile cache
- fix(map): backdrop pane with pointer-events:none so MVT hover works
- perf(map): debounce center + skip city-level fetches under vector tiles

## [3.56.0] - 2026-05-23

### Features

- feat(schemas): add Carte and Spark schema tabs

### Bug Fixes

- fix(tiles): cities start at z9 (depts z7-8 · cities+arr z9-14)

## [3.55.1] - 2026-05-22

### Bug Fixes

- fix(tiles): no zoom overlap between layers (regions z4-6, depts z7-9, cities z10-14)
- perf(spark): broadcast cities, partition JDBC reads, kryo serializer

## [3.55.0] - 2026-05-22

### Features

- feat(geo): +17 countries to world admin-1 (1353 features, 1047 with population)

## [3.54.0] - 2026-05-22

### Features

- feat(tiles): multi-layer mbtiles (regions+departments+cities)

## [3.53.1] - 2026-05-22

### Bug Fixes

- fix(tiles): persist metric-ranges + recompute on startup
- fix(reviews): show loader while sentiment/wordcloud/reviews are pending

## [3.53.0] - 2026-05-22

### Features

- feat(map): revert departments to zoom 7, push city detail to zoom 10
- feat(map): show departments from zoom 8 (was 7)

### Bug Fixes

- fix(tiles): return 204 for empty tiles to silence DevTools 404 noise

## [3.52.0] - 2026-05-22

### Features

- feat(map): quantile choropleth scale + admin tile rebuild trigger

## [3.51.0] - 2026-05-22

### Features

- feat: Belgian communes layer + city name search

## [3.50.1] - 2026-05-22

### Bug Fixes

- fix(map): arrondissements URL + VectorGrid z=8 storm
- fix(map): stitch Russia/Fiji across the antimeridian

## [3.50.0] - 2026-05-22

### Features

- feat: arrondissements MVT + Data schema page + index rationale

## [3.49.0] - 2026-05-22

### Features

- feat(geo): bake admin-1 population for 39 EU/G20 countries (94% coverage)

## [3.48.0] - 2026-05-22

### Features

- feat(webapp): React Flow schemas + K8s/Flux pipeline + tippecanoe bash fix

## [3.47.0] - 2026-05-22

### Features

- feat(tiles): bake DVF stats into commune MVT features

### Bug Fixes

- fix(tiles): include batch.tiles package in JPA repository scan
- fix(webapp): catch VectorGrid fetch rejections so failed tiles don't leak as unhandled promises

## [3.46.1] - 2026-05-22

### Bug Fixes

- fix(test): migrate TransactionServiceTest to new statsRepository mock
- fix(db): splitStatements:false on changeset 017 DO block

## [3.46.0] - 2026-05-22

### Features

- feat(observability): Grafana alerts + smoke CronJob (closes #2)
- feat: IRIS boundaries endpoint + city page section (closes #10)
- feat: Spark perf tuning + comparable sales popup (closes #11)

## [3.45.0] - 2026-05-22

### Features

- feat(webapp): page Schémas avec sous-routes (architecture, BDD, devops)
- feat(webapp): hover tooltip + highlight on CityVectorGridLayer

### Bug Fixes

- perf(jvm): G1GC + 100ms pause target + RAM percentage (closes #4)
- perf(db): backfill autovacuum tuning to historical partitions (closes #5)
- perf(backend): port computeStats to DB-side aggregate (closes #3)
- perf(webapp): drop bounds debounce from 200ms to 50ms
- fix(webapp): mark VectorGrid features interactive so clicks fire
- perf(webapp): redraw VectorGrid only on choropleth range shifts
- fix(webapp): keep MapContainer mounted when vector tiles drive the city layer
- fix(webapp): move CityVectorGridLayer ref sync out of render
- fix(webapp): stop CityVectorGridLayer from remounting on every pan
- fix(webapp): cap VectorGrid at maxNativeZoom=14 to keep polygons visible past z14

## [3.44.0] - 2026-05-21

### Features

- feat(backend): perf tuning (#4 #5) + import skeletons (#7 #10 #11)

### Bug Fixes

- fix(webapp): bind leaflet.vectorgrid to the app's Leaflet instance under Vite

## [3.43.1] - 2026-05-21

### Bug Fixes

- perf: cache + indexed prefix lookup for IRIS/comparables/quarterly endpoints

## [3.43.0] - 2026-05-21

### Features

- feat(api): foundations for comparable sales endpoint (issue #11)
- feat(api): foundations for INSEE Filosofi IRIS indicators (issue #10)

## [3.42.0] - 2026-05-21

### Features

- feat(stats): quarterly price /m² timeline per commune (issue #9)
- feat(webapp): enable vector tiles in prod build (issue #6)
- feat(map): wire CityVectorGridLayer behind VITE_USE_VECTOR_TILES flag (issue #6)
- feat(map): ship CityVectorGridLayer component (issue #6 step 3, not wired yet)

### Bug Fixes

- fix(webapp): disable vector tiles flag until frontend rendering is debugged

## [3.41.0] - 2026-05-21

### Features

- feat(tiles): add vector tile endpoint scaffolding for commune polygons (issue #6 step 1)

## [3.40.2] - 2026-05-21

### Bug Fixes

- perf(webapp): persist React Query cache in localStorage and prefetch refdata at idle

## [3.40.1] - 2026-05-21

### Bug Fixes

- fix(cache): revert Jackson typing to EVERYTHING and bump Redis namespace to v2

## [3.40.0] - 2026-05-21

### Features

- feat(geo): bake spherical area into world-admin1, extend GeographicLevel for NUTS/COUNTRY tiers

## [3.39.0] - 2026-05-21

### Features

- feat(observability): expose Prometheus metrics via Micrometer at /actuator/prometheus

### Bug Fixes

- perf(http): 60s browser cache on /transactions/heatpoints and /markers
- perf(db): composite index indicators(level, code, category)

## [3.38.1] - 2026-05-21

### Bug Fixes

- perf(webapp): lazy-load Recharts in price and sentiment charts

## [3.38.0] - 2026-05-21

### Features

- feat(map): clickable markers for individual DVF transactions

## [3.37.0] - 2026-05-21

### Features

- feat(events): relay SSE batch events across pods via Redis pub/sub
- feat(geocoding): geocode DVF transactions via BAN, expose precise heatmap

### Bug Fixes

- perf(http): cache /geo /regions /departments for 24h, keep stats at 5min
- perf(db): index cities.department_code and add trigram GIN on cities.name
- fix(map): heatmap follows polygon shape via boundary sampling

## [3.36.0] - 2026-05-01

### Features

- feat(map): density + GDP per capita at world zoom, dispersed heatmap

### Bug Fixes

- fix(map): hide Natural Earth borders for countries with precise overlay
- fix(map): restore world wrap-around with duplicated country borders

## [3.35.0] - 2026-05-01

### Features

- feat(webapp): hide polygon borders in heat/bubbles modes and enrich CityPage

### Bug Fixes

- fix(map): recover FR/NO/Somaliland codes, stop world wrap, default to world view

## [3.34.0] - 2026-04-30

### Features

- feat(map): world admin-1 boundaries for ~38 EU + G20 countries

## [3.33.0] - 2026-04-30

### Features

- feat(map): Belgium provinces overlay + indicator top-left + restore world view
- feat(map): always show France data, world borders are just backdrop

### Bug Fixes

- fix(map): hide foreground polygons in pure heat mode

## [3.32.0] - 2026-04-30

### Features

- feat(map): keep world country outlines visible at all zooms + horizontal loop

## [3.31.1] - 2026-04-30

### Bug Fixes

- fix(geo): commit Natural Earth countries.geojson + gitignore exception

## [3.31.0] - 2026-04-30

### Features

- feat(map): world view — Natural Earth country boundaries at low zoom

### Bug Fixes

- perf(map): debounce bounds + canvas renderer + bbox cache + stable layerKey

## [3.30.0] - 2026-04-30

### Features

- feat: ProblemDetail + validation + Resilience4j + RUM web vitals + error boundary

### Bug Fixes

- perf(webapp): nginx tuning + pre-gzip + index.html preconnect

## [3.29.2] - 2026-04-30

### Bug Fixes

- perf: GZIP compression + Mongo bulk insert + bundle analyzer

## [3.29.1] - 2026-04-30

### Bug Fixes

- fix(routing): conditional on spring.datasource.replica.url
- perf: streaming bulk inserts + HTTP cache + read replica routing

## [3.29.0] - 2026-04-30

### Features

- feat(admin): add per-year stats refresh button

### Bug Fixes

- fix(dvf): use plain ANALYZE instead of VACUUM ANALYZE after swap

## [3.28.1] - 2026-04-30

### Bug Fixes

- perf: BRIN index on mutation_date + actuator dump endpoints + lazy routes

## [3.28.0] - 2026-04-30

### Features

- feat(admin): hide DVF rows for years not served by data.gouv.fr

### Bug Fixes

- fix(stats): revert SQL refactor of computeStats, add unscoped-call guard
- fix(stats): aggregate /transactions/stats in SQL instead of streaming rows

## [3.27.2] - 2026-04-30

### Bug Fixes

- perf: pre-aggregate DVF stats + univocity parser + DPE batch insert

## [3.27.1] - 2026-04-30

### Bug Fixes

- fix(stats): deduplicate DVF mutations to fix avg price + price/m²

## [3.27.0] - 2026-04-30

### Features

- feat(admin): truncate DVF year + restrict dropdown to 2021+

## [3.26.0] - 2026-04-30

### Features

- feat(dvf): resumable HTTP download with retry + Range

## [3.25.0] - 2026-04-30

### Features

- feat(admin): show last-import duration on job cards and per DVF year

### Bug Fixes

- fix(batch): prevent orphan JDBC sessions and stuck Spring Batch jobs

## [3.24.0] - 2026-04-29

### Features

- feat(admin): per-year inline trigger button in DVF partition table

### Bug Fixes

- perf(imports): drop @Transactional from DPE/Health/Indicator services
- perf(reviews): parallelize generation and bump batch size
- perf(insee): drop @Transactional + parallelize fetchCommunes calls
- fix(reviews): drop @Transactional from importReviews to avoid 6h Postgres tx leak

## [3.23.3] - 2026-04-28

### Bug Fixes

- fix: update dependency com.fasterxml.jackson.core:jackson-databind to v2.21.3
- fix: update dependency com.fasterxml.jackson.core:jackson-databind to v2.21.3

## [3.23.2] - 2026-04-28

### Bug Fixes

- fix(dvf): wire id sequence on shadow table + 409 for job already running
- fix(dvf): wire id sequence on shadow table + 409 for job already running

## [3.23.1] - 2026-04-28

### Bug Fixes

- fix(dvf): @JobScope on dvfImportStep + table layout for partition stats
- fix(dvf): @JobScope on dvfImportStep + table layout for partition stats

## [3.23.0] - 2026-04-28

### Features

- feat(admin): DVF follow-ups — cleanup, perf, partition stats, bulk import
- feat(admin): DVF follow-ups — cleanup, perf, partition stats, bulk import

### Bug Fixes

- fix(liquibase): splitStatements:false on the DO block in 006

## [3.22.0] - 2026-04-28

### Features

- feat(admin): DVF import speed-up + Redis cache controls
- feat(admin): DVF import speed-up + Redis cache controls

## [3.21.0] - 2026-04-27

### Features

- feat: admin console with auth + on-demand import triggers

### Bug Fixes

- fix(rest-api): include api.auth in JPA scan so AdminUserRepository is wired
- fix(rest-api): correct SecurityContextRepository import path

## [3.20.3] - 2026-04-27

### Bug Fixes

- perf(rest-api): use Postgres COPY FROM STDIN for DVF bulk insert
- fix(rest-api): make Feature.geometry round-trippable through Redis

## [3.20.2] - 2026-04-27

### Bug Fixes

- perf(rest-api): tune JDBC batching for DVF bulk insert

## [3.20.1] - 2026-04-27

### Bug Fixes

- fix: auto-flush stale Redis cache entries on startup
- fix: add Spark timeout, increase HikariCP pool, fix thread starvation

## [3.20.0] - 2026-04-27

### Features

- feat: add admin recompute-stats endpoint and wire Spark DVF stats

### Bug Fixes

- fix: increase dialog z-index above map layer (z-2000)

## [3.19.4] - 2026-04-27

### Bug Fixes

- fix: price/sqm mismatch, transaction detail endpoint, wider selects, dept cities list

## [3.19.3] - 2026-04-27

### Bug Fixes

- fix: DVF insee code bug + departments API type mismatch
- fix: PropertyType enum values + Leaflet z-index overlay

## [3.19.2] - 2026-04-27

### Bug Fixes

- fix: Redis cache serialization for records + ResponseEntity migration

## [3.19.1] - 2026-04-27

### Bug Fixes

- fix: DVF import uses per-batch transactions instead of one giant TX

## [3.19.0] - 2026-04-27

### Features

- feat(webapp): rewrite to map-first architecture with floating panels

### Bug Fixes

- fix: raise header z-index above Leaflet map layers
- fix(cache): configure Jackson ObjectMapper for record deserialization
- fix(map): propagate h-full through FranceMap container chain
- fix(api): batch city stats requests to avoid URL length overflow
- fix(test): update ExplorerPage tests to match panel-compact labels

## [3.18.0] - 2026-04-27

### Features

- feat(webapp): rewrite to map-first architecture with floating panels

### Bug Fixes

- fix(cache): configure Jackson ObjectMapper for record deserialization
- fix(map): propagate h-full through FranceMap container chain
- fix(api): batch city stats requests to avoid URL length overflow
- fix(test): update ExplorerPage tests to match panel-compact labels

## [3.17.0] - 2026-04-27

### Features

- feat(batch): enable all auto-downloadable imports in prod

## [3.16.0] - 2026-04-27

### Features

- feat: agent-first development improvements

### Bug Fixes

- fix(ci): remove H2 config conflicting with Testcontainers PostgreSQL
- fix(ci): disable DinD TLS — certs not shared in K8s runner pod
- fix(ci): add -am flag to build common module before rest-api tests
- fix(backend): apply spotless formatting to regression tests
- fix: not checked is present in spring cache

## [3.15.0] - 2026-04-26

### Features

- feat: per-city stats endpoint + arrondissements drilldown at zoom >= 12

### Bug Fixes

- fix: format
- fix: changed zoom level

## [3.14.0] - 2026-04-26

### Features

- feat(webapp): expand button on map (toggle 500px <-> 78vh) and remove side padding when expanded

### Bug Fixes

- fix: format
- fix: use @class json typing + homepedia: key prefix to safely share redis; close tooltips on map drag
- fix(api): swallow Redis errors in cache layer to degrade gracefully

## [3.13.0] - 2026-04-26

### Features

- feat(api): add Redis cache for geo/refdata/stats/reviews + invalidate after batch imports
- feat(webapp): merge commune polygons across all visible departments + drop redundant city markers
- feat(webapp): commune polygons (real INSEE borders) at zoom>=9 with city-level metric
- feat(webapp): show current layer/zoom indicator + listen on zoom (not just zoomend)
- feat(webapp): auto-detect department under center at zoom>=9 + city markers sized by population
- feat(webapp): polygon clicks fly into the feature locally without URL change
- feat(webapp): always show aggregated metric on map (drop uniform orange default)
- feat(webapp): zoom-driven map layers (regions <7, departments >=7) with appropriate aggregation
- feat(webapp): redesign map (carto voyager, sunset palette, legend, zoom-aware city markers)

### Bug Fixes

- fix(webapp): satisfy eslint (no non-null assertion, set-state-in-effect, useless assignment)
- fix(webapp): subtle hover on default polygons (no orange flood) + reset on zoomstart

## [3.12.2] - 2026-04-25

### Bug Fixes

- fix(api): enable mongo repositories scan in com.homepedia.common
- fix: update spark.version to v3.5.8
- fix: update spark.version to v3.5.8

## [3.12.1] - 2026-04-25

### Bug Fixes

- fix(build): align spark-jobs parent version with root + register module in ferrflow
- fix(build): copy spark-jobs pom into rest-api docker build context

## [3.12.0] - 2026-04-25

### Features

- feat(api): server-sent events for real-time batch progress + frontend banner
- feat(spark): add spark-jobs module with DVF aggregation job + cluster in compose
- feat(api): migrate city reviews to MongoDB (relational + non-relational mix)
- feat(webapp): add heatmap layer alongside choropleth and bubbles

### Bug Fixes

- fix(build): drop shade transformer + make leaflet.heat type augment instead of replace

## [3.11.1] - 2026-04-25

### Bug Fixes

- fix(build): pin springdoc to 2.8.17 (v3 requires spring boot 3.6+)

## [3.11.0] - 2026-04-25

### Features

- feat(batch): generic indicator import for economy, education, environment, infrastructure
- feat(webapp): choropleth + bubble layers with metric/style selectors
- feat(api): aggregate stats endpoints for region/department choropleth
- feat(webapp): show city markers on department map and highlight active feature
- feat(webapp): persistent URL-driven map with auto-zoom on selection

### Bug Fixes

- fix(webapp): bump select dropdown z-index above leaflet map controls
- fix(api): silence 404 logs (NoResourceFoundException) in exception handler
- fix(ci): drop common pom from cache key (gitlab limits to 2 files)
- fix: update dependency org.springdoc:springdoc-openapi-starter-webmvc-ui to v3
- fix(api): silence client disconnect noise in exception handler
- fix: update dependency org.springdoc:springdoc-openapi-starter-webmvc-ui to v3

## [3.10.0] - 2026-04-25

### Features

- feat(webapp): add async dropdown autocomplete on region search
- feat(api): aggregate population and area on regions and departments from communes

### Bug Fixes

- perf(webapp): memoize FranceMap and stabilize click handlers
- fix(batch): paginate INSEE communes import per department to avoid timeout

## [3.9.1] - 2026-04-25

### Bug Fixes

- fix(batch): use dedicated flag for startup runner to avoid clashing with spring boot auto-runner

## [3.9.0] - 2026-04-25

### Features

- feat(batch): log scheduled job duration on completion and failure

### Bug Fixes

- fix(webapp): set page title to HomePedia

## [3.8.0] - 2026-04-25

### Features

- feat(batch): provision spring batch schema via liquibase changeset

### Bug Fixes

- fix(build): align root pom version with child modules (3.7.0)
- fix(build): reorder root pom + pin spring-boot 3.5.14 to work around ferrflow xml selector
- fix(ci): drop redundant cd webapp from script (pwd already set by before_script)
- fix(batch): remove @EnableBatchProcessing so spring boot creates metadata tables

## [3.7.0] - 2026-04-25

### Features

- feat(batch): add cron scheduler for periodic data imports

### Bug Fixes

- fix(build): align root pom version with child modules (3.6.0)
- fix(build): revert spring-boot parent to 3.5.14 (3.6.0 not on maven central)

## [3.6.0] - 2026-04-24

### Features

- feat(ci): migrate from semantic-release to FerrFlow
- feat(ci): migrate from semantic-release to FerrFlow

## [1.1.1](https://gitlab.com/t-dat-902/homepedia/compare/rest-api-v1.1.0...rest-api-v1.1.1) (2026-04-24)


### Bug Fixes

* update dependency org.apache.commons:commons-collections4 to v4.5.0 ([22ffa2c](https://gitlab.com/t-dat-902/homepedia/commit/22ffa2c099f23e348822346ada8976f88d7982cd))
* update dependency org.projectlombok:lombok to v1.18.46 ([8ae7af9](https://gitlab.com/t-dat-902/homepedia/commit/8ae7af9fc31fb76c548f541faae882c80069193a))

# [1.1.0](https://gitlab.com/t-dat-902/homepedia/compare/rest-api-v1.0.2...rest-api-v1.1.0) (2026-04-24)


### Bug Fixes

* **ci:** replace @semantic-release/npm with exec for webapp ([fd512d8](https://gitlab.com/t-dat-902/homepedia/commit/fd512d85d88a0863bf6c8f5a261e7ebf87057919))


### Features

* **batch:** add auto-download support for DVF, DPE, and Health datasets ([8020100](https://gitlab.com/t-dat-902/homepedia/commit/80201005cb99084e07bf742ed133c0cb094d8bca))

## [1.0.2](https://gitlab.com/t-dat-902/homepedia/compare/rest-api-v1.0.1...rest-api-v1.0.2) (2026-04-24)


### Bug Fixes

* **ci:** configure DOCKER_HOST for DinD on Kubernetes executor ([728423d](https://gitlab.com/t-dat-902/homepedia/commit/728423d47f39a75c18c06264a9aca3c7adf6c0f3))
* **ci:** use Buildah instead of Docker for K8s builds ([1f958c7](https://gitlab.com/t-dat-902/homepedia/commit/1f958c7d96a2a55cf5afd5bc4c8dae6441288af1))
* update Dockerfiles for current project structure ([ebab65b](https://gitlab.com/t-dat-902/homepedia/commit/ebab65b0c614b8b5d29f4e5b05b1e69ddf11e7ee))

## [1.0.1](https://gitlab.com/t-dat-902/homepedia/compare/rest-api-v1.0.0...rest-api-v1.0.1) (2026-04-24)


### Bug Fixes

* sync child POM parent versions to 1.0.0 ([1673532](https://gitlab.com/t-dat-902/homepedia/commit/16735323af25a80ab4d3b4a21b859a64ec77ba04))

# 1.0.0 (2026-04-24)


### Bug Fixes

* **ci:** allow manual pipeline triggers on [skip ci] commits ([a73f2d4](https://gitlab.com/t-dat-902/homepedia/commit/a73f2d47b39e54489d4d5f3c8e1b19d75c9caaf8))
* **ci:** convert releaserc YAML to CJS for semantic-release ([94c454a](https://gitlab.com/t-dat-902/homepedia/commit/94c454a7c4f75ce8edd68fa92a3f5c8c063b8730))
* **ci:** fix webapp lint cd issue and remove data-pipeline jobs ([2e21ad2](https://gitlab.com/t-dat-902/homepedia/commit/2e21ad2cd4ff72c290c279ffd64c31398666359d))
* **ci:** target parent pom for versions:set in release ([08bb71b](https://gitlab.com/t-dat-902/homepedia/commit/08bb71b9d504be3b9c3c701d7344aa1788820f4a))
* **ci:** use fully qualified image names for buildah compatibility ([974fe14](https://gitlab.com/t-dat-902/homepedia/commit/974fe14a44d28e09af512f5704342a16fa80ae51))
* remove final for jpa entity ([5394f35](https://gitlab.com/t-dat-902/homepedia/commit/5394f35717748a6d9c9b706d565b3cc7402a9640))
* remove package-lock.json reference from app Dockerfiles ([a516ec5](https://gitlab.com/t-dat-902/homepedia/commit/a516ec5ab63c9cebb98ff7fe00d6173616081024))
* resolve @types/node conflict between workspaces for npm ci ([e00c427](https://gitlab.com/t-dat-902/homepedia/commit/e00c427c6fb675a52bdf66da0855bfb7267bb6cc))
* trigger jobs main-branch only, optional needs, remove automergeType ([dde4bfa](https://gitlab.com/t-dat-902/homepedia/commit/dde4bfa22d094b31c2e6beb3c481ecf540ef4324))
* update dependency org.springframework.boot:spring-boot-starter-parent to v3.5.13 ([daac8db](https://gitlab.com/t-dat-902/homepedia/commit/daac8dbdb54547c40386c831c9245c474440e02b))
* update dependency org.springframework.boot:spring-boot-starter-parent to v3.5.14 ([a57bde9](https://gitlab.com/t-dat-902/homepedia/commit/a57bde9aeb7a38dfe50546bbcb7af1c0c60b9b4d))
* **webapp:** resolve ESLint errors in pages ([8208016](https://gitlab.com/t-dat-902/homepedia/commit/8208016cdf78df650ba61debb68b114df5a49d2c))


### Features

* add city review scraper and sentiment analysis module ([91e04d4](https://gitlab.com/t-dat-902/homepedia/commit/91e04d45a4c326eb8ed76e23d561750cc8ef2b7d))
* initialize monorepo with CI/CD, versioning, and Traefik reverse proxy ([66be0c7](https://gitlab.com/t-dat-902/homepedia/commit/66be0c75bb30c829992790349d753de70a9f1c48))
* java multi modules ([256e2de](https://gitlab.com/t-dat-902/homepedia/commit/256e2de76120a08d0b21796cd55b9ccc9f0ce7f5))
* **webapp:** add reviews page with word cloud and sentiment analysis ([ce8b4a2](https://gitlab.com/t-dat-902/homepedia/commit/ce8b4a298fffd52f0c206c3c1c0b9efb5e49836b))
* **webapp:** build complete frontend with pages, components, and API hooks ([6a7c936](https://gitlab.com/t-dat-902/homepedia/commit/6a7c93652e2bf7877a8ea65ec65d885feddfbb6b))

# Changelog
