# app

## [0.81.0] - 2026-07-05

### Features

- feat(map): drill into regions, departments and country for reviews and pollution
- feat(stats): expose pollution at region/department/country and aggregate reviews by area

## [0.80.0] - 2026-07-05

### Features

- feat(map): flag DVF-unavailable departments (Alsace-Moselle, Mayotte)
- feat(tiles): bake region and department DVF aggregates into tiles
- feat(geo): fill admin-2 coverage for 27 countries from geoBoundaries (CC0)
- feat(map): add Belgium provinces and communes to world GADM tiles
- feat(map): default to the 3D globe view (opt out with VITE_USE_MAPLIBRE=false)
- feat(country): add population growth, internet, CO2, health/education spend and Gini indicators
- feat(country): add OECD house price index for major non-EU economies
- feat(country): import Eurostat house price index and expose it as a map metric
- feat(country): expand World Bank import with growth, inflation, unemployment, life expectancy, urban and density
- feat(country): import current World Bank metrics for all countries and overlay onto geo data
- feat(map): bake complete country metrics (population, gdp, gdpPerCapita, area) into world tiles
- feat(map): mirror 2D zoom bands on the globe (regions to departments to communes)
- feat(map): render real centroid bubbles on the globe in bubbles mode
- feat(map): add OSM POI layer, hover-address and bubbles fallback to the globe
- feat(map): port heatmap and transaction pins to the globe and cap world tiles for perf
- feat(map): add opt-in MapLibre globe view with choropleth alongside the 2D map
- feat(map): allow dezooming to the full planet view (minZoom 2 to 1)
- feat(spark): make the dvf aggregate output table configurable via --output-table
- feat(spark): run dvf aggregation as a kubernetes job reading the transactions table
- feat(spark): optional LSH nearest-neighbour matcher for comparable sales
- feat(dvf): import geo-dvf per-mutation coordinates for precise heatmap and markers
- feat(admin): surface live import phase in job status cards
- feat(map): add pollution choropleth metric (GES 1-7)
- feat(indicators): persist GES class from DPE feed and expose city pollution score
- feat(pois): backend proxy + Redis 7d cache in front of Overpass
- feat(map): satellite basemap + OSM POIs + hover reverse-geocode + admin-3 expansion
- feat(map): overlay OSM street/POI labels at z>=9 for non-FR detail
- feat(world): admin-1 detail page + global search + admin-3 MVT layer
- feat(world): admin-1 round 4 — +50 missing countries + GDP backfill
- feat(world): admin-2 for USA/BRA/MEX/CHN/IND + ~50 missing countries
- feat(admin): expose ExitStatus message under FAILED jobs
- feat(world): MVT pipeline for countries + admin-1 + admin-2
- feat(world): +48 admin-2 countries with adaptive simplification
- feat(world): +30 admin-1 countries (Africa/MENA/Central America/Pacific)
- feat(admin): add rebuild tiles button + auto evict geo cache
- feat(world): admin-2 layer for European countries past zoom 7
- feat(world): +25 admin-1 countries + bake area and gdp metrics
- feat(reviews): loading checkpoints + skeleton placeholders
- feat(tiles): bake IRIS layer for sub-commune drilldown at z=13-14
- feat(schemas): add Carte and Spark schema tabs
- feat(geo): +17 countries to world admin-1 (1353 features, 1047 with population)
- feat(tiles): multi-layer mbtiles (regions+departments+cities)
- feat(map): zoom-aware country border weight
- feat(map): revert departments to zoom 7, push city detail to zoom 10
- feat(map): show departments from zoom 8 (was 7)
- feat(map): quantile choropleth scale + admin tile rebuild trigger
- feat: Belgian communes layer + city name search
- feat: arrondissements MVT + Data schema page + index rationale
- feat(geo): bake admin-1 population for 39 EU/G20 countries (94% coverage)
- feat(webapp): React Flow schemas + K8s/Flux pipeline + tippecanoe bash fix
- feat(tiles): bake DVF stats into commune MVT features
- feat(observability): Grafana alerts + smoke CronJob (closes #2)
- feat: IRIS boundaries endpoint + city page section (closes #10)
- feat: Spark perf tuning + comparable sales popup (closes #11)
- feat(webapp): page Schémas avec sous-routes (architecture, BDD, devops)
- feat(webapp): hover tooltip + highlight on CityVectorGridLayer
- feat(backend): perf tuning (#4 #5) + import skeletons (#7 #10 #11)
- feat(api): foundations for comparable sales endpoint (issue #11)
- feat(api): foundations for INSEE Filosofi IRIS indicators (issue #10)
- feat(stats): quarterly price /m² timeline per commune (issue #9)
- feat(webapp): enable vector tiles in prod build (issue #6)
- feat(map): wire CityVectorGridLayer behind VITE_USE_VECTOR_TILES flag (issue #6)
- feat(map): ship CityVectorGridLayer component (issue #6 step 3, not wired yet)
- feat(tiles): add vector tile endpoint scaffolding for commune polygons (issue #6 step 1)
- feat(geo): bake spherical area into world-admin1, extend GeographicLevel for NUTS/COUNTRY tiers
- feat(observability): expose Prometheus metrics via Micrometer at /actuator/prometheus
- feat(map): clickable markers for individual DVF transactions
- feat(events): relay SSE batch events across pods via Redis pub/sub
- feat(geocoding): geocode DVF transactions via BAN, expose precise heatmap
- feat(map): density + GDP per capita at world zoom, dispersed heatmap
- feat(webapp): hide polygon borders in heat/bubbles modes and enrich CityPage
- feat(map): world admin-1 boundaries for ~38 EU + G20 countries
- feat(map): Belgium provinces overlay + indicator top-left + restore world view
- feat(map): always show France data, world borders are just backdrop
- feat(map): keep world country outlines visible at all zooms + horizontal loop
- feat(map): world view — Natural Earth country boundaries at low zoom
- feat: ProblemDetail + validation + Resilience4j + RUM web vitals + error boundary
- feat(admin): add per-year stats refresh button
- feat(admin): hide DVF rows for years not served by data.gouv.fr
- feat(admin): truncate DVF year + restrict dropdown to 2021+
- feat(dvf): resumable HTTP download with retry + Range
- feat(admin): show last-import duration on job cards and per DVF year
- feat(admin): per-year inline trigger button in DVF partition table
- feat(admin): DVF follow-ups — cleanup, perf, partition stats, bulk import
- feat(admin): DVF import speed-up + Redis cache controls
- feat(admin): DVF follow-ups — cleanup, perf, partition stats, bulk import
- feat(admin): DVF import speed-up + Redis cache controls
- feat: admin console with auth + on-demand import triggers
- feat: add admin recompute-stats endpoint and wire Spark DVF stats
- feat(webapp): rewrite to map-first architecture with floating panels
- feat(batch): enable all auto-downloadable imports in prod
- feat: agent-first development improvements
- feat: per-city stats endpoint + arrondissements drilldown at zoom >= 12
- feat(webapp): expand button on map (toggle 500px <-> 78vh) and remove side padding when expanded
- feat(api): add Redis cache for geo/refdata/stats/reviews + invalidate after batch imports
- feat(webapp): merge commune polygons across all visible departments + drop redundant city markers
- feat(webapp): commune polygons (real INSEE borders) at zoom>=9 with city-level metric
- feat(webapp): show current layer/zoom indicator + listen on zoom (not just zoomend)
- feat(webapp): auto-detect department under center at zoom>=9 + city markers sized by population
- feat(webapp): polygon clicks fly into the feature locally without URL change
- feat(webapp): always show aggregated metric on map (drop uniform orange default)
- feat(webapp): zoom-driven map layers (regions <7, departments >=7) with appropriate aggregation
- feat(webapp): redesign map (carto voyager, sunset palette, legend, zoom-aware city markers)
- feat(api): server-sent events for real-time batch progress + frontend banner
- feat(spark): add spark-jobs module with DVF aggregation job + cluster in compose
- feat(api): migrate city reviews to MongoDB (relational + non-relational mix)
- feat(webapp): add heatmap layer alongside choropleth and bubbles
- feat(batch): generic indicator import for economy, education, environment, infrastructure
- feat(webapp): choropleth + bubble layers with metric/style selectors
- feat(api): aggregate stats endpoints for region/department choropleth
- feat(webapp): show city markers on department map and highlight active feature
- feat(webapp): persistent URL-driven map with auto-zoom on selection
- feat(webapp): add async dropdown autocomplete on region search
- feat(api): aggregate population and area on regions and departments from communes
- feat(batch): log scheduled job duration on completion and failure
- feat(batch): provision spring batch schema via liquibase changeset
- feat(batch): add cron scheduler for periodic data imports
- feat(ci): migrate from semantic-release to FerrFlow
- feat(ci): migrate from semantic-release to FerrFlow
- feat(batch): adapt importers for real open data sources
- feat(batch): add auto-download support for DVF, DPE, and Health datasets
- feat(webapp): add reviews page with word cloud and sentiment analysis
- feat: add city review scraper and sentiment analysis module
- feat(webapp): build complete frontend with pages, components, and API hooks
- feat: java multi modules
- feat: initialize monorepo with CI/CD, versioning, and Traefik reverse proxy

### Bug Fixes

- fix(reviews): drop unused Mongo text index so startup index build does not crashloop the pod
- perf(reviews): materialise the cityInseeCode Mongo index via auto-index-creation
- fix(stats): drop stray pollutionScore getter from transaction projection
- fix(map): aggregate GES pollution at region and department level
- perf(tiles): build layers in parallel with incremental tile-join cache
- perf(tiles): cap tile size at 2MB instead of unlimited to speed regeneration
- fix(data): import municipal arrondissements for Paris/Lyon/Marseille DVF
- fix(map): bridge choropleth zoom bands to remove z9 transition flash
- fix(map): stop showing GADM 'NA' placeholder as world tile labels
- perf(map): skip the 26 MB world admin-2 GeoJSON when world tiles are on
- perf(api): cache the binary heatpoints endpoint like its JSON sibling (60s + ETag)
- perf(api): serve heatpoints as a packed Float32 binary endpoint
- perf(rest-api): add bbox+mutation_date covering index for transaction markers
- perf(webapp): memoize merged geo FeatureCollections to avoid map source re-parse
- fix(geocoder): avoid long-lived transaction and infinite re-geocoding of unresolved rows
- perf(map): fetch heatpoints as a packed Float32 buffer with JSON fallback
- perf(map): color globe layers from a prop-derived range on tile load instead of waiting for idle
- fix(map): start globe region layer at z5 so it no longer stacks on the country fill
- fix(tiles): serialize city and world tile builds to halve peak disk usage
- perf(db): partial index for geocode backlog count to stop connection-leak warnings
- fix(db): self-heal dept_dvf_stats column types on startup before schema validation
- fix(spark): truncate dept_dvf_stats on overwrite to preserve column types
- fix(map): use lightweight countries layer at planet zoom and color it via metricByCode
- fix(map): align globe zone layers to the tippecanoe zoom bands and add countries/admin3
- perf(spark): stage geo-dvf to partitioned parquet and read it columnar in dvf aggregate
- perf(heatpoints): finer 44m grid now that transactions are geocoded
- fix(transactions): tolerate null property_type and date in marker rows
- fix(map): guard FitBounds against non-finite bounds and make heat kernel zoom-aware
- fix(stats): move GES lateral comments out of native query string
- fix(indicators): use DpeRawRecord#dpeLabelGes accessor for GES aggregation
- perf(reviews): stream generation into bounded queue with parallel mongo writers
- fix(tiles): pull regions/departements geojson from github after geo.api.gouv.fr regression
- fix(map): isolate vector tile panes, gate layers by zoom band, clear stale canvas
- fix(webapp): rewrite /api prefix in vite dev proxy
- fix(world): skip MVT countries draw so SVG choropleth shows at z<5
- fix(map): mount world MVT layer below city so clicks at z=9+ work
- fix(ui): format Select trigger labels instead of leaking raw values
- fix(dpe): skip rows whose insee code exceeds the varchar(9) column
- perf(spark): COPY FROM STDIN + drop/recreate indexes + PK pre-sort
- fix(map): create backdrop pane synchronously to avoid _removePath crash
- perf(webapp): useDeferredValue on bounds, unbundle recharts, SW tile cache
- fix(map): backdrop pane with pointer-events:none so MVT hover works
- perf(map): debounce center + skip city-level fetches under vector tiles
- fix(tiles): cities start at z9 (depts z7-8 · cities+arr z9-14)
- fix(tiles): no zoom overlap between layers (regions z4-6, depts z7-9, cities z10-14)
- perf(spark): broadcast cities, partition JDBC reads, kryo serializer
- fix(geo): patch 5 NA/? entries in world admin-1 (UK England, Munster, Zuid-Holland, Kyiv)
- fix(tiles): persist metric-ranges + recompute on startup
- fix(reviews): show loader while sentiment/wordcloud/reviews are pending
- fix(tiles): return 204 for empty tiles to silence DevTools 404 noise
- fix(map): arrondissements URL + VectorGrid z=8 storm
- fix(map): stitch Russia/Fiji across the antimeridian
- fix(tiles): include batch.tiles package in JPA repository scan
- fix(webapp): catch VectorGrid fetch rejections so failed tiles don't leak as unhandled promises
- fix(test): migrate TransactionServiceTest to new statsRepository mock
- fix(db): splitStatements:false on changeset 017 DO block
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
- fix(webapp): bind leaflet.vectorgrid to the app's Leaflet instance under Vite
- perf: cache + indexed prefix lookup for IRIS/comparables/quarterly endpoints
- fix(webapp): disable vector tiles flag until frontend rendering is debugged
- perf(webapp): persist React Query cache in localStorage and prefetch refdata at idle
- fix(cache): revert Jackson typing to EVERYTHING and bump Redis namespace to v2
- perf(http): 60s browser cache on /transactions/heatpoints and /markers
- perf(db): composite index indicators(level, code, category)
- perf(webapp): lazy-load Recharts in price and sentiment charts
- perf(http): cache /geo /regions /departments for 24h, keep stats at 5min
- perf(db): index cities.department_code and add trigram GIN on cities.name
- fix(map): heatmap follows polygon shape via boundary sampling
- fix(map): hide Natural Earth borders for countries with precise overlay
- fix(map): restore world wrap-around with duplicated country borders
- fix(map): recover FR/NO/Somaliland codes, stop world wrap, default to world view
- fix(map): hide foreground polygons in pure heat mode
- fix(geo): commit Natural Earth countries.geojson + gitignore exception
- perf(map): debounce bounds + canvas renderer + bbox cache + stable layerKey
- perf(webapp): nginx tuning + pre-gzip + index.html preconnect
- perf: GZIP compression + Mongo bulk insert + bundle analyzer
- fix(routing): conditional on spring.datasource.replica.url
- perf: streaming bulk inserts + HTTP cache + read replica routing
- fix(dvf): use plain ANALYZE instead of VACUUM ANALYZE after swap
- perf: BRIN index on mutation_date + actuator dump endpoints + lazy routes
- fix(stats): revert SQL refactor of computeStats, add unscoped-call guard
- fix(stats): aggregate /transactions/stats in SQL instead of streaming rows
- perf: pre-aggregate DVF stats + univocity parser + DPE batch insert
- fix(stats): deduplicate DVF mutations to fix avg price + price/m²
- fix(batch): prevent orphan JDBC sessions and stuck Spring Batch jobs
- perf(imports): drop @Transactional from DPE/Health/Indicator services
- perf(reviews): parallelize generation and bump batch size
- perf(insee): drop @Transactional + parallelize fetchCommunes calls
- fix(reviews): drop @Transactional from importReviews to avoid 6h Postgres tx leak
- fix: update dependency com.fasterxml.jackson.core:jackson-databind to v2.21.3
- fix: update dependency com.fasterxml.jackson.core:jackson-databind to v2.21.3
- fix(dvf): wire id sequence on shadow table + 409 for job already running
- fix(dvf): wire id sequence on shadow table + 409 for job already running
- fix(dvf): @JobScope on dvfImportStep + table layout for partition stats
- fix(dvf): @JobScope on dvfImportStep + table layout for partition stats
- fix(liquibase): splitStatements:false on the DO block in 006
- fix(rest-api): include api.auth in JPA scan so AdminUserRepository is wired
- fix(rest-api): correct SecurityContextRepository import path
- perf(rest-api): use Postgres COPY FROM STDIN for DVF bulk insert
- fix(rest-api): make Feature.geometry round-trippable through Redis
- perf(rest-api): tune JDBC batching for DVF bulk insert
- fix: auto-flush stale Redis cache entries on startup
- fix: add Spark timeout, increase HikariCP pool, fix thread starvation
- fix: increase dialog z-index above map layer (z-2000)
- fix: price/sqm mismatch, transaction detail endpoint, wider selects, dept cities list
- fix: DVF insee code bug + departments API type mismatch
- fix: PropertyType enum values + Leaflet z-index overlay
- fix: Redis cache serialization for records + ResponseEntity migration
- fix: DVF import uses per-batch transactions instead of one giant TX
- fix: raise header z-index above Leaflet map layers
- fix(cache): configure Jackson ObjectMapper for record deserialization
- fix(map): propagate h-full through FranceMap container chain
- fix(api): batch city stats requests to avoid URL length overflow
- fix(test): update ExplorerPage tests to match panel-compact labels
- fix(ci): remove H2 config conflicting with Testcontainers PostgreSQL
- fix(ci): disable DinD TLS — certs not shared in K8s runner pod
- fix(ci): add -am flag to build common module before rest-api tests
- fix(backend): apply spotless formatting to regression tests
- fix: not checked is present in spring cache
- fix: format
- fix: changed zoom level
- fix: format
- fix: use @class json typing + homepedia: key prefix to safely share redis; close tooltips on map drag
- fix(api): swallow Redis errors in cache layer to degrade gracefully
- fix(webapp): satisfy eslint (no non-null assertion, set-state-in-effect, useless assignment)
- fix(webapp): subtle hover on default polygons (no orange flood) + reset on zoomstart
- fix(api): enable mongo repositories scan in com.homepedia.common
- fix: update spark.version to v3.5.8
- fix: update spark.version to v3.5.8
- fix(build): align spark-jobs parent version with root + register module in ferrflow
- fix(build): copy spark-jobs pom into rest-api docker build context
- fix(build): drop shade transformer + make leaflet.heat type augment instead of replace
- fix(build): pin springdoc to 2.8.17 (v3 requires spring boot 3.6+)
- fix(webapp): bump select dropdown z-index above leaflet map controls
- fix(api): silence 404 logs (NoResourceFoundException) in exception handler
- fix(ci): drop common pom from cache key (gitlab limits to 2 files)
- fix: update dependency org.springdoc:springdoc-openapi-starter-webmvc-ui to v3
- fix: update dependency org.springdoc:springdoc-openapi-starter-webmvc-ui to v3
- fix(api): silence client disconnect noise in exception handler
- perf(webapp): memoize FranceMap and stabilize click handlers
- fix(batch): paginate INSEE communes import per department to avoid timeout
- fix(batch): use dedicated flag for startup runner to avoid clashing with spring boot auto-runner
- fix(webapp): set page title to HomePedia
- fix(build): align root pom version with child modules (3.7.0)
- fix(build): reorder root pom + pin spring-boot 3.5.14 to work around ferrflow xml selector
- fix(ci): drop redundant cd webapp from script (pwd already set by before_script)
- fix(batch): remove @EnableBatchProcessing so spring boot creates metadata tables
- fix(build): align root pom version with child modules (3.6.0)
- fix(build): revert spring-boot parent to 3.5.14 (3.6.0 not on maven central)
- fix: update dependency org.apache.commons:commons-collections4 to v4.5.0
- fix: update dependency org.projectlombok:lombok to v1.18.46
- fix(ci): replace @semantic-release/npm with exec for webapp
- fix: update Dockerfiles for current project structure
- fix(ci): use Buildah instead of Docker for K8s builds
- fix(ci): configure DOCKER_HOST for DinD on Kubernetes executor
- fix: sync child POM parent versions to 1.0.0
- fix(ci): target parent pom for versions:set in release
- fix(ci): convert releaserc YAML to CJS for semantic-release
- fix(webapp): resolve ESLint errors in pages
- fix(ci): fix webapp lint cd issue and remove data-pipeline jobs
- fix: remove final for jpa entity
- fix: update dependency org.springframework.boot:spring-boot-starter-parent to v3.5.14
- fix: update dependency org.springframework.boot:spring-boot-starter-parent to v3.5.13
- fix: trigger jobs main-branch only, optional needs, remove automergeType
- fix: remove package-lock.json reference from app Dockerfiles
- fix(ci): use fully qualified image names for buildah compatibility
- fix: resolve @types/node conflict between workspaces for npm ci

### Refactoring

- refactor(map): extract layer visibility toggling into the layers module
- refactor(map): extract globe map creation and basemap config into a module
- refactor(map): extract globe layer installation into a dedicated module
- refactor(map): extract geojson source builders and a useGeoJsonSource hook
- refactor(map): extract globe choropleth and layer logic into a dedicated module
- refactor: merge data-pipeline into rest-api with Spring Batch + Liquibase
- refactor(webapp): migrate all pages and components to shadcn/ui
- refactor: rename data-pipeline package, use GeographicLevel enum, add ParseUtils

## [0.79.0] - 2026-06-18

### Features

- feat(map): flag DVF-unavailable departments (Alsace-Moselle, Mayotte)
- feat(tiles): bake region and department DVF aggregates into tiles

### Bug Fixes

- perf(tiles): cap tile size at 2MB instead of unlimited to speed regeneration
- fix(data): import municipal arrondissements for Paris/Lyon/Marseille DVF

## [0.78.0] - 2026-06-05

### Features

- feat(geo): fill admin-2 coverage for 27 countries from geoBoundaries (CC0)
- feat(map): add Belgium provinces and communes to world GADM tiles

### Bug Fixes

- fix(map): bridge choropleth zoom bands to remove z9 transition flash

## [0.77.0] - 2026-06-04

### Features

- feat(map): default to the 3D globe view (opt out with VITE_USE_MAPLIBRE=false)

## [0.76.10] - 2026-06-04

### Bug Fixes

- fix(map): stop showing GADM 'NA' placeholder as world tile labels

## [0.76.9] - 2026-06-04

### Bug Fixes

- perf(map): skip the 26 MB world admin-2 GeoJSON when world tiles are on
- perf(api): cache the binary heatpoints endpoint like its JSON sibling (60s + ETag)
- perf(api): serve heatpoints as a packed Float32 binary endpoint

## [0.76.8] - 2026-06-03

### Bug Fixes

- perf(rest-api): add bbox+mutation_date covering index for transaction markers
- perf(webapp): memoize merged geo FeatureCollections to avoid map source re-parse
- fix(geocoder): avoid long-lived transaction and infinite re-geocoding of unresolved rows

## [0.76.7] - 2026-06-02

### Bug Fixes

- perf(map): fetch heatpoints as a packed Float32 buffer with JSON fallback

## [0.76.6] - 2026-06-02

## [0.76.5] - 2026-06-02

## [0.76.4] - 2026-06-02

## [0.76.3] - 2026-06-02

## [0.76.2] - 2026-06-02

## [0.76.1] - 2026-06-02

### Bug Fixes

- perf(map): color globe layers from a prop-derived range on tile load instead of waiting for idle

## [0.76.0] - 2026-05-31

### Features

- feat(country): add population growth, internet, CO2, health/education spend and Gini indicators
- feat(country): add OECD house price index for major non-EU economies

### Bug Fixes

- fix(map): start globe region layer at z5 so it no longer stacks on the country fill
- fix(tiles): serialize city and world tile builds to halve peak disk usage
- perf(db): partial index for geocode backlog count to stop connection-leak warnings

## [0.75.0] - 2026-05-29

### Features

- feat(country): import Eurostat house price index and expose it as a map metric
- feat(country): expand World Bank import with growth, inflation, unemployment, life expectancy, urban and density
- feat(country): import current World Bank metrics for all countries and overlay onto geo data

### Bug Fixes

- fix(db): self-heal dept_dvf_stats column types on startup before schema validation
- fix(spark): truncate dept_dvf_stats on overwrite to preserve column types

## [0.74.0] - 2026-05-29

### Features

- feat(map): bake complete country metrics (population, gdp, gdpPerCapita, area) into world tiles

## [0.73.2] - 2026-05-29

### Bug Fixes

- fix(map): use lightweight countries layer at planet zoom and color it via metricByCode

## [0.73.1] - 2026-05-29

### Bug Fixes

- fix(map): align globe zone layers to the tippecanoe zoom bands and add countries/admin3

## [0.73.0] - 2026-05-29

### Features

- feat(map): mirror 2D zoom bands on the globe (regions to departments to communes)

## [0.72.0] - 2026-05-28

### Features

- feat(map): render real centroid bubbles on the globe in bubbles mode

## [0.71.0] - 2026-05-28

### Features

- feat(map): add OSM POI layer, hover-address and bubbles fallback to the globe

## [0.70.0] - 2026-05-28

### Features

- feat(map): port heatmap and transaction pins to the globe and cap world tiles for perf

## [0.69.0] - 2026-05-28

### Features

- feat(map): add opt-in MapLibre globe view with choropleth alongside the 2D map

## [0.68.0] - 2026-05-28

### Features

- feat(map): allow dezooming to the full planet view (minZoom 2 to 1)
- feat(spark): make the dvf aggregate output table configurable via --output-table
- feat(spark): run dvf aggregation as a kubernetes job reading the transactions table
- feat(spark): optional LSH nearest-neighbour matcher for comparable sales

### Bug Fixes

- perf(spark): stage geo-dvf to partitioned parquet and read it columnar in dvf aggregate

## [0.67.0] - 2026-05-28

### Features

- feat(dvf): import geo-dvf per-mutation coordinates for precise heatmap and markers

### Bug Fixes

- perf(heatpoints): finer 44m grid now that transactions are geocoded
- fix(transactions): tolerate null property_type and date in marker rows
- fix(map): guard FitBounds against non-finite bounds and make heat kernel zoom-aware

## [0.66.0] - 2026-05-28

### Features

- feat(admin): surface live import phase in job status cards

## [0.65.0] - 2026-05-28

### Features

- feat(map): add pollution choropleth metric (GES 1-7)
- feat(indicators): persist GES class from DPE feed and expose city pollution score

### Bug Fixes

- fix(stats): move GES lateral comments out of native query string
- fix(indicators): use DpeRawRecord#dpeLabelGes accessor for GES aggregation

## [0.64.2] - 2026-05-24

### Bug Fixes

- perf(reviews): stream generation into bounded queue with parallel mongo writers
- fix(tiles): pull regions/departements geojson from github after geo.api.gouv.fr regression
- fix(map): isolate vector tile panes, gate layers by zoom band, clear stale canvas
- fix(webapp): rewrite /api prefix in vite dev proxy

## [0.64.1] - 2026-05-23

### Bug Fixes

- fix(world): skip MVT countries draw so SVG choropleth shows at z<5

## [0.64.0] - 2026-05-23

### Features

- feat(pois): backend proxy + Redis 7d cache in front of Overpass

## [0.63.0] - 2026-05-23

### Features

- feat(map): satellite basemap + OSM POIs + hover reverse-geocode + admin-3 expansion

## [0.62.0] - 2026-05-23

### Features

- feat(map): overlay OSM street/POI labels at z>=9 for non-FR detail

## [0.61.1] - 2026-05-23

### Bug Fixes

- fix(map): mount world MVT layer below city so clicks at z=9+ work

## [0.61.0] - 2026-05-23

### Features

- feat(world): admin-1 detail page + global search + admin-3 MVT layer
- feat(world): admin-1 round 4 — +50 missing countries + GDP backfill
- feat(world): admin-2 for USA/BRA/MEX/CHN/IND + ~50 missing countries

## [0.60.1] - 2026-05-23

### Bug Fixes

- fix(ui): format Select trigger labels instead of leaking raw values
- fix(dpe): skip rows whose insee code exceeds the varchar(9) column

## [0.60.0] - 2026-05-23

### Features

- feat(admin): expose ExitStatus message under FAILED jobs

## [0.59.0] - 2026-05-23

### Features

- feat(world): MVT pipeline for countries + admin-1 + admin-2
- feat(world): +48 admin-2 countries with adaptive simplification
- feat(world): +30 admin-1 countries (Africa/MENA/Central America/Pacific)

## [0.58.0] - 2026-05-23

### Features

- feat(admin): add rebuild tiles button + auto evict geo cache

## [0.57.0] - 2026-05-23

### Features

- feat(world): admin-2 layer for European countries past zoom 7

## [0.56.0] - 2026-05-23

### Features

- feat(world): +25 admin-1 countries + bake area and gdp metrics

### Bug Fixes

- perf(spark): COPY FROM STDIN + drop/recreate indexes + PK pre-sort

## [0.55.0] - 2026-05-23

### Features

- feat(reviews): loading checkpoints + skeleton placeholders

## [0.54.0] - 2026-05-23

### Features

- feat(tiles): bake IRIS layer for sub-commune drilldown at z=13-14

## [0.53.5] - 2026-05-23

### Bug Fixes

- fix(map): create backdrop pane synchronously to avoid _removePath crash

## [0.53.4] - 2026-05-23

### Bug Fixes

- perf(webapp): useDeferredValue on bounds, unbundle recharts, SW tile cache

## [0.53.3] - 2026-05-23

### Bug Fixes

- fix(map): backdrop pane with pointer-events:none so MVT hover works

## [0.53.2] - 2026-05-23

### Bug Fixes

- perf(map): debounce center + skip city-level fetches under vector tiles

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
