# app

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
