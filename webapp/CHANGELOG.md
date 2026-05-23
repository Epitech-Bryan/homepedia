# app

## [0.53.1] - 2026-05-23

### Bug Fixes

- fix(tiles): cities start at z9 (depts z7-8 · cities+arr z9-14)

## [0.53.0] - 2026-05-23

### Features

- feat(schemas): add Carte and Spark schema tabs
- feat(geo): +17 countries to world admin-1 (1353 features, 1047 with population)

### Bug Fixes

- fix(tiles): no zoom overlap between layers (regions z4-6, depts z7-9, cities z10-14)
- perf(spark): broadcast cities, partition JDBC reads, kryo serializer

## [0.52.0] - 2026-05-22

### Features

- feat(tiles): multi-layer mbtiles (regions+departments+cities)

### Bug Fixes

- fix(geo): patch 5 NA/? entries in world admin-1 (UK England, Munster, Zuid-Holland, Kyiv)
- fix(tiles): persist metric-ranges + recompute on startup

## [0.51.1] - 2026-05-22

### Bug Fixes

- fix(reviews): show loader while sentiment/wordcloud/reviews are pending

## [0.51.0] - 2026-05-22

### Features

- feat(map): zoom-aware country border weight

### Bug Fixes

- fix(tiles): return 204 for empty tiles to silence DevTools 404 noise

## [0.50.0] - 2026-05-22

### Features

- feat(map): revert departments to zoom 7, push city detail to zoom 10

## [0.49.0] - 2026-05-22

### Features

- feat(map): show departments from zoom 8 (was 7)

## [0.48.0] - 2026-05-22

### Features

- feat(map): quantile choropleth scale + admin tile rebuild trigger

## [0.47.0] - 2026-05-22

### Features

- feat: Belgian communes layer + city name search

## [0.46.2] - 2026-05-22

### Bug Fixes

- fix(map): arrondissements URL + VectorGrid z=8 storm

## [0.46.1] - 2026-05-22

### Bug Fixes

- fix(map): stitch Russia/Fiji across the antimeridian

## [0.46.0] - 2026-05-22

### Features

- feat: arrondissements MVT + Data schema page + index rationale
- feat(geo): bake admin-1 population for 39 EU/G20 countries (94% coverage)

## [0.45.0] - 2026-05-22

### Features

- feat(webapp): React Flow schemas + K8s/Flux pipeline + tippecanoe bash fix

## [0.44.0] - 2026-05-22

### Features

- feat(tiles): bake DVF stats into commune MVT features

### Bug Fixes

- fix(tiles): include batch.tiles package in JPA repository scan

## [0.43.1] - 2026-05-22

### Bug Fixes

- fix(webapp): catch VectorGrid fetch rejections so failed tiles don't leak as unhandled promises
- fix(test): migrate TransactionServiceTest to new statsRepository mock
- fix(db): splitStatements:false on changeset 017 DO block

## [0.43.0] - 2026-05-22

### Features

- feat(observability): Grafana alerts + smoke CronJob (closes #2)
- feat: IRIS boundaries endpoint + city page section (closes #10)
- feat: Spark perf tuning + comparable sales popup (closes #11)

## [0.42.0] - 2026-05-22

### Features

- feat(webapp): page Schémas avec sous-routes (architecture, BDD, devops)

### Bug Fixes

- perf(jvm): G1GC + 100ms pause target + RAM percentage (closes #4)
- perf(db): backfill autovacuum tuning to historical partitions (closes #5)
- perf(backend): port computeStats to DB-side aggregate (closes #3)

## [0.41.0] - 2026-05-22

### Features

- feat(webapp): hover tooltip + highlight on CityVectorGridLayer

### Bug Fixes

- perf(webapp): drop bounds debounce from 200ms to 50ms
- fix(webapp): mark VectorGrid features interactive so clicks fire

## [0.40.4] - 2026-05-22

### Bug Fixes

- perf(webapp): redraw VectorGrid only on choropleth range shifts

## [0.40.3] - 2026-05-22

### Bug Fixes

- fix(webapp): keep MapContainer mounted when vector tiles drive the city layer

## [0.40.2] - 2026-05-22

### Bug Fixes

- fix(webapp): move CityVectorGridLayer ref sync out of render
- fix(webapp): stop CityVectorGridLayer from remounting on every pan

## [0.40.1] - 2026-05-22

### Bug Fixes

- fix(webapp): cap VectorGrid at maxNativeZoom=14 to keep polygons visible past z14

## [0.40.0] - 2026-05-21

### Features

- feat(backend): perf tuning (#4 #5) + import skeletons (#7 #10 #11)

## [0.39.1] - 2026-05-21

### Bug Fixes

- fix(webapp): bind leaflet.vectorgrid to the app's Leaflet instance under Vite

## [0.39.0] - 2026-05-21

### Features

- feat(api): foundations for comparable sales endpoint (issue #11)
- feat(api): foundations for INSEE Filosofi IRIS indicators (issue #10)

### Bug Fixes

- perf: cache + indexed prefix lookup for IRIS/comparables/quarterly endpoints

## [0.38.0] - 2026-05-21

### Features

- feat(stats): quarterly price /m² timeline per commune (issue #9)

## [0.37.1] - 2026-05-21

### Bug Fixes

- fix(webapp): disable vector tiles flag until frontend rendering is debugged

## [0.37.0] - 2026-05-21

### Features

- feat(webapp): enable vector tiles in prod build (issue #6)

## [0.36.0] - 2026-05-21

### Features

- feat(map): wire CityVectorGridLayer behind VITE_USE_VECTOR_TILES flag (issue #6)

## [0.35.0] - 2026-05-21

### Features

- feat(map): ship CityVectorGridLayer component (issue #6 step 3, not wired yet)
- feat(tiles): add vector tile endpoint scaffolding for commune polygons (issue #6 step 1)

## [0.34.0] - 2026-05-21

### Features

- feat(geo): bake spherical area into world-admin1, extend GeographicLevel for NUTS/COUNTRY tiers
- feat(observability): expose Prometheus metrics via Micrometer at /actuator/prometheus

### Bug Fixes

- perf(webapp): persist React Query cache in localStorage and prefetch refdata at idle
- fix(cache): revert Jackson typing to EVERYTHING and bump Redis namespace to v2
- perf(http): 60s browser cache on /transactions/heatpoints and /markers
- perf(db): composite index indicators(level, code, category)

## [0.33.1] - 2026-05-21

### Bug Fixes

- perf(webapp): lazy-load Recharts in price and sentiment charts

## [0.33.0] - 2026-05-21

### Features

- feat(map): clickable markers for individual DVF transactions

## [0.32.0] - 2026-05-21

### Features

- feat(events): relay SSE batch events across pods via Redis pub/sub
- feat(geocoding): geocode DVF transactions via BAN, expose precise heatmap

### Bug Fixes

- perf(http): cache /geo /regions /departments for 24h, keep stats at 5min
- perf(db): index cities.department_code and add trigram GIN on cities.name

## [0.31.1] - 2026-05-01

### Bug Fixes

- fix(map): heatmap follows polygon shape via boundary sampling

## [0.31.0] - 2026-05-01

### Features

- feat(map): density + GDP per capita at world zoom, dispersed heatmap

## [0.30.3] - 2026-05-01

### Bug Fixes

- fix(map): hide Natural Earth borders for countries with precise overlay

## [0.30.2] - 2026-05-01

### Bug Fixes

- fix(map): restore world wrap-around with duplicated country borders

## [0.30.1] - 2026-05-01

### Bug Fixes

- fix(map): recover FR/NO/Somaliland codes, stop world wrap, default to world view

## [0.30.0] - 2026-05-01

### Features

- feat(webapp): hide polygon borders in heat/bubbles modes and enrich CityPage

## [0.29.0] - 2026-04-30

### Features

- feat(map): world admin-1 boundaries for ~38 EU + G20 countries

## [0.28.0] - 2026-04-30

### Features

- feat(map): Belgium provinces overlay + indicator top-left + restore world view

## [0.27.1] - 2026-04-30

### Bug Fixes

- fix(map): hide foreground polygons in pure heat mode

## [0.27.0] - 2026-04-30

### Features

- feat(map): always show France data, world borders are just backdrop

## [0.26.0] - 2026-04-30

### Features

- feat(map): keep world country outlines visible at all zooms + horizontal loop

## [0.25.1] - 2026-04-30

### Bug Fixes

- fix(geo): commit Natural Earth countries.geojson + gitignore exception

## [0.25.0] - 2026-04-30

### Features

- feat(map): world view — Natural Earth country boundaries at low zoom

## [0.24.1] - 2026-04-30

### Bug Fixes

- perf(map): debounce bounds + canvas renderer + bbox cache + stable layerKey

## [0.24.0] - 2026-04-30

### Features

- feat: ProblemDetail + validation + Resilience4j + RUM web vitals + error boundary

## [0.23.2] - 2026-04-30

### Bug Fixes

- perf(webapp): nginx tuning + pre-gzip + index.html preconnect

## [0.23.1] - 2026-04-30

### Bug Fixes

- perf: GZIP compression + Mongo bulk insert + bundle analyzer
- fix(routing): conditional on spring.datasource.replica.url
- perf: streaming bulk inserts + HTTP cache + read replica routing

## [0.23.0] - 2026-04-30

### Features

- feat(admin): add per-year stats refresh button

### Bug Fixes

- fix(dvf): use plain ANALYZE instead of VACUUM ANALYZE after swap

## [0.22.1] - 2026-04-30

### Bug Fixes

- perf: BRIN index on mutation_date + actuator dump endpoints + lazy routes

## [0.22.0] - 2026-04-30

### Features

- feat(admin): hide DVF rows for years not served by data.gouv.fr

### Bug Fixes

- fix(stats): revert SQL refactor of computeStats, add unscoped-call guard
- fix(stats): aggregate /transactions/stats in SQL instead of streaming rows
- perf: pre-aggregate DVF stats + univocity parser + DPE batch insert
- fix(stats): deduplicate DVF mutations to fix avg price + price/m²

## [0.21.0] - 2026-04-30

### Features

- feat(admin): truncate DVF year + restrict dropdown to 2021+
- feat(dvf): resumable HTTP download with retry + Range

## [0.20.0] - 2026-04-30

### Features

- feat(admin): show last-import duration on job cards and per DVF year

### Bug Fixes

- fix(batch): prevent orphan JDBC sessions and stuck Spring Batch jobs

## [0.19.0] - 2026-04-29

### Features

- feat(admin): per-year inline trigger button in DVF partition table

### Bug Fixes

- perf(imports): drop @Transactional from DPE/Health/Indicator services
- perf(reviews): parallelize generation and bump batch size
- perf(insee): drop @Transactional + parallelize fetchCommunes calls
- fix(reviews): drop @Transactional from importReviews to avoid 6h Postgres tx leak
- fix: update dependency com.fasterxml.jackson.core:jackson-databind to v2.21.3
- fix: update dependency com.fasterxml.jackson.core:jackson-databind to v2.21.3
- fix(dvf): wire id sequence on shadow table + 409 for job already running
- fix(dvf): wire id sequence on shadow table + 409 for job already running

## [0.18.1] - 2026-04-28

### Bug Fixes

- fix(dvf): @JobScope on dvfImportStep + table layout for partition stats
- fix(dvf): @JobScope on dvfImportStep + table layout for partition stats

## [0.18.0] - 2026-04-28

### Features

- feat(admin): DVF follow-ups — cleanup, perf, partition stats, bulk import
- feat(admin): DVF follow-ups — cleanup, perf, partition stats, bulk import

### Bug Fixes

- fix(liquibase): splitStatements:false on the DO block in 006

## [0.17.0] - 2026-04-28

### Features

- feat(admin): DVF import speed-up + Redis cache controls
- feat(admin): DVF import speed-up + Redis cache controls

## [0.16.0] - 2026-04-27

### Features

- feat: admin console with auth + on-demand import triggers

### Bug Fixes

- fix(rest-api): include api.auth in JPA scan so AdminUserRepository is wired
- fix(rest-api): correct SecurityContextRepository import path
- perf(rest-api): use Postgres COPY FROM STDIN for DVF bulk insert
- fix(rest-api): make Feature.geometry round-trippable through Redis
- perf(rest-api): tune JDBC batching for DVF bulk insert
- fix: auto-flush stale Redis cache entries on startup
- fix: add Spark timeout, increase HikariCP pool, fix thread starvation

## [0.15.0] - 2026-04-27

### Features

- feat: add admin recompute-stats endpoint and wire Spark DVF stats

## [0.14.7] - 2026-04-27

### Bug Fixes

- fix: increase dialog z-index above map layer (z-2000)

## [0.14.6] - 2026-04-27

### Bug Fixes

- fix: price/sqm mismatch, transaction detail endpoint, wider selects, dept cities list

## [0.14.5] - 2026-04-27

### Bug Fixes

- fix: DVF insee code bug + departments API type mismatch

## [0.14.4] - 2026-04-27

### Bug Fixes

- fix: PropertyType enum values + Leaflet z-index overlay
- fix: Redis cache serialization for records + ResponseEntity migration
- fix: DVF import uses per-batch transactions instead of one giant TX

## [0.14.3] - 2026-04-27

### Bug Fixes

- fix: raise header z-index above Leaflet map layers
- fix(cache): configure Jackson ObjectMapper for record deserialization
- fix(map): propagate h-full through FranceMap container chain

## [0.14.2] - 2026-04-27

### Bug Fixes

- fix(map): propagate h-full through FranceMap container chain

## [0.14.1] - 2026-04-27

### Bug Fixes

- fix(api): batch city stats requests to avoid URL length overflow

## [0.14.0] - 2026-04-27

### Features

- feat(webapp): rewrite to map-first architecture with floating panels
- feat(batch): enable all auto-downloadable imports in prod

### Bug Fixes

- fix(test): update ExplorerPage tests to match panel-compact labels

## [0.13.0] - 2026-04-27

### Features

- feat: agent-first development improvements

### Bug Fixes

- fix(ci): remove H2 config conflicting with Testcontainers PostgreSQL
- fix(ci): disable DinD TLS — certs not shared in K8s runner pod
- fix(ci): add -am flag to build common module before rest-api tests
- fix(backend): apply spotless formatting to regression tests
- fix: not checked is present in spring cache

## [0.12.0] - 2026-04-26

### Features

- feat: per-city stats endpoint + arrondissements drilldown at zoom >= 12

### Bug Fixes

- fix: format

## [0.11.1] - 2026-04-26

### Bug Fixes

- fix: changed zoom level

## [0.11.0] - 2026-04-26

### Features

- feat(webapp): expand button on map (toggle 500px <-> 78vh) and remove side padding when expanded

### Bug Fixes

- fix: format
- fix: use @class json typing + homepedia: key prefix to safely share redis; close tooltips on map drag
- fix(api): swallow Redis errors in cache layer to degrade gracefully

## [0.10.0] - 2026-04-26

### Features

- feat(api): add Redis cache for geo/refdata/stats/reviews + invalidate after batch imports
- feat(webapp): merge commune polygons across all visible departments + drop redundant city markers
- feat(webapp): commune polygons (real INSEE borders) at zoom>=9 with city-level metric
- feat(webapp): show current layer/zoom indicator + listen on zoom (not just zoomend)
- feat(webapp): auto-detect department under center at zoom>=9 + city markers sized by population
- feat(webapp): polygon clicks fly into the feature locally without URL change

### Bug Fixes

- fix(webapp): satisfy eslint (no non-null assertion, set-state-in-effect, useless assignment)

## [0.9.0] - 2026-04-25

### Features

- feat(webapp): always show aggregated metric on map (drop uniform orange default)
- feat(webapp): zoom-driven map layers (regions <7, departments >=7) with appropriate aggregation
- feat(webapp): redesign map (carto voyager, sunset palette, legend, zoom-aware city markers)

### Bug Fixes

- fix(webapp): subtle hover on default polygons (no orange flood) + reset on zoomstart
- fix(api): enable mongo repositories scan in com.homepedia.common
- fix: update spark.version to v3.5.8
- fix: update spark.version to v3.5.8
- fix(build): align spark-jobs parent version with root + register module in ferrflow
- fix(build): copy spark-jobs pom into rest-api docker build context

## [0.8.0] - 2026-04-25

### Features

- feat(webapp): zoom-driven map layers (regions <7, departments >=7) with appropriate aggregation
- feat(webapp): redesign map (carto voyager, sunset palette, legend, zoom-aware city markers)

### Bug Fixes

- fix(webapp): subtle hover on default polygons (no orange flood) + reset on zoomstart
- fix(api): enable mongo repositories scan in com.homepedia.common
- fix: update spark.version to v3.5.8
- fix: update spark.version to v3.5.8
- fix(build): align spark-jobs parent version with root + register module in ferrflow
- fix(build): copy spark-jobs pom into rest-api docker build context

## [0.7.0] - 2026-04-25

### Features

- feat(webapp): redesign map (carto voyager, sunset palette, legend, zoom-aware city markers)

### Bug Fixes

- fix(api): enable mongo repositories scan in com.homepedia.common
- fix: update spark.version to v3.5.8
- fix: update spark.version to v3.5.8
- fix(build): align spark-jobs parent version with root + register module in ferrflow
- fix(build): copy spark-jobs pom into rest-api docker build context

## [0.6.0] - 2026-04-25

### Features

- feat(api): server-sent events for real-time batch progress + frontend banner
- feat(spark): add spark-jobs module with DVF aggregation job + cluster in compose
- feat(api): migrate city reviews to MongoDB (relational + non-relational mix)
- feat(webapp): add heatmap layer alongside choropleth and bubbles

### Bug Fixes

- fix(build): drop shade transformer + make leaflet.heat type augment instead of replace
- fix(build): pin springdoc to 2.8.17 (v3 requires spring boot 3.6+)

## [0.5.0] - 2026-04-25

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

## [0.4.0] - 2026-04-25

### Features

- feat(webapp): add async dropdown autocomplete on region search
- feat(api): aggregate population and area on regions and departments from communes

### Bug Fixes

- perf(webapp): memoize FranceMap and stabilize click handlers
- fix(batch): paginate INSEE communes import per department to avoid timeout
- fix(batch): use dedicated flag for startup runner to avoid clashing with spring boot auto-runner

## [0.3.0] - 2026-04-25

### Features

- feat(batch): log scheduled job duration on completion and failure
- feat(batch): provision spring batch schema via liquibase changeset
- feat(batch): add cron scheduler for periodic data imports

### Bug Fixes

- fix(webapp): set page title to HomePedia
- fix(build): align root pom version with child modules (3.7.0)
- fix(build): reorder root pom + pin spring-boot 3.5.14 to work around ferrflow xml selector
- fix(ci): drop redundant cd webapp from script (pwd already set by before_script)
- fix(batch): remove @EnableBatchProcessing so spring boot creates metadata tables
- fix(build): align root pom version with child modules (3.6.0)
- fix(build): revert spring-boot parent to 3.5.14 (3.6.0 not on maven central)

## [0.2.0] - 2026-04-24

### Features

- feat(ci): migrate from semantic-release to FerrFlow
- feat(ci): migrate from semantic-release to FerrFlow

## 0.1.4

### Patch Changes

- fix: trigger jobs main-branch only, optional needs, remove automergeType

## 0.1.3

### Patch Changes

- fix: remove package-lock.json reference from app Dockerfiles

## 0.1.2

### Patch Changes

- fix(ci): use fully qualified image names for buildah compatibility

## 0.1.1

### Patch Changes

- fix: resolve @types/node conflict between workspaces for npm ci
