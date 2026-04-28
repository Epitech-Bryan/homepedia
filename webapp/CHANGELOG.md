# app

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
