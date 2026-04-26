# app

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
