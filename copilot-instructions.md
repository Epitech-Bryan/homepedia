# Homepedia — Copilot Instructions

## Project Overview

Homepedia is an interactive platform providing comprehensive information and analysis on the French housing market. Users explore price trends, regional variations, demographic factors, energy diagnostics, and city reviews across regions, departments, and cities.

## Architecture

```
homepedia/                    ← pnpm monorepo root
├── backend/                  ← Maven multi-module (Java 21, Spring Boot 3.5)
│   ├── common/               ← Shared JPA entities, records, utilities
│   ├── rest-api/             ← Spring Boot REST API (port 8080)
│   │   └── batch/            ← Spring Batch ETL jobs (inside rest-api)
│   └── project-data/         ← Raw CSV/data source files (git-ignored large files)
├── webapp/                   ← React 19 + TypeScript + Vite frontend
├── compose.yml               ← Docker Compose (Traefik, backend, PostgreSQL)
└── docs/adr/                 ← Architecture Decision Records
```

## Running Locally

```bash
# Start all services
docker compose up -d

# Access
# Webapp:    http://homepedia.localhost
# API:       http://homepedia.localhost/api
# Traefik:   http://localhost:8090

# Backend only (requires PostgreSQL running)
cd backend && mvn spring-boot:run -pl rest-api

# Frontend only
cd webapp && pnpm dev
```

## Java Conventions (backend)

### Package-by-Feature

Organize code by domain feature, not by technical layer:

```
com.homepedia.common.city/
  City.java               ← JPA entity (@Getter @Setter @NoArgsConstructor)
  CitySummary.java         ← record (projection / DTO)
  CityRepository.java      ← Spring Data repository
```

The REST API uses classic layered structure:

```
com.homepedia.api/
  controller/             ← thin controllers, no business logic
  service/                ← business logic, uses MapStruct mappers
  mapper/                 ← MapStruct interfaces (convert* method naming)
  config/                 ← exception handlers, web config
```

### Lombok

Use Lombok annotations everywhere:
- `@Getter @Setter` on JPA entities
- `@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)` for JPA entities
- `@RequiredArgsConstructor` for constructor injection (services, controllers)
- `@Slf4j` instead of manual Logger declaration
- No `@Data` or `@Builder` on entities

### MapStruct Mappers

All entity-to-DTO mapping is done via MapStruct interfaces in `mapper/` package:

```java
@Mapper(componentModel = "spring")
public interface CityMapper {
    @Mapping(source = "department.code", target = "departmentCode")
    CitySummary convertToSummary(City city);
    List<CitySummary> convertToSummaryList(List<City> cities);
}
```

Method naming: always `convert*` (e.g. `convertToSummary`, `convertToSummaryList`).

### HATEOAS for Pagination

Paginated endpoints return `PagedModel<EntityModel<T>>` using Spring HATEOAS:

```java
@GetMapping
public PagedModel<EntityModel<CitySummary>> findAll(Pageable pageable) {
    final var page = cityService.findAll(pageable);
    return pagedResourcesAssembler.toModel(page);
}
```

### Records for DTOs

Use Java records for all value objects, DTOs, projections, filter/query parameter objects, API responses. No `Optional` in records — use nullable fields:

```java
public record CitySummary(String inseeCode, String name, String department, long population) {}
public record TransactionFilter(String cityCode, Integer year, BigDecimal minPrice) {}
```

### Variables

Use `final` on local variables, and prefer `var` with `final`:

```java
final var regions = regionRepository.findAll();
final var error = new ErrorResponse(400, "Bad Request", msg, Instant.now());
```

### Utility Classes

Use Apache Commons utilities instead of manual null/empty checks:

```java
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.collections4.CollectionUtils;

if (StringUtils.isNotBlank(regionCode)) { ... }
if (CollectionUtils.isEmpty(transactions)) { ... }
```

### No Comments

Code must be self-documenting through clear naming. Do not add Javadoc, inline comments, or TODO comments. If code needs a comment to be understood, rename or restructure it.

### No Hidden Coupling

Each feature package is self-contained. Cross-feature dependencies go through well-defined service interfaces. Do not inject repositories from other packages — inject the service instead.

### SOLID Principles

- **S**: One service per feature, one responsibility per class
- **O**: Use sealed interfaces / strategy pattern for extensibility
- **L**: Entity inheritance must be substitutable
- **I**: Keep interfaces focused — split large ones
- **D**: Depend on abstractions (Spring DI), not concrete implementations

### Sealed Interfaces for Indicator Types

```java
public sealed interface Indicator permits EconomicIndicator, DemographicIndicator, EnergyIndicator {}
```

### Error Handling

Use `GlobalExceptionHandler` with `@RestControllerAdvice`. Return `ErrorResponse` records with appropriate HTTP status codes. Never expose stack traces.

## Frontend Conventions (webapp)

### Stack

- React 19 + TypeScript (strict mode)
- Vite for builds
- TanStack React Query for server state
- React Router for navigation
- Mapbox GL JS / react-map-gl for maps
- Recharts for charts
- Tailwind CSS for styling

### Component Structure

```
src/
  pages/           ← route-level components
  components/      ← reusable UI components
  hooks/           ← custom React hooks
  api/             ← API client functions (one file per domain)
  types/           ← shared TypeScript types
  lib/             ← utilities
```

### API Layer

Each domain has a dedicated API file returning typed data:

```typescript
// api/cities.ts
export async function getCities(params: CityFilter): Promise<PagedResponse<CitySummary>> { ... }
```

Use TanStack Query hooks for data fetching — never call fetch directly in components.

### TypeScript

- Strict mode (`"strict": true`) — no `any`
- Use `interface` for object shapes, `type` for unions/intersections
- All API responses must be typed

## Data Sources

| Source | URL | Data |
|--------|-----|------|
| DVF | data.gouv.fr/datasets/dvf | Real estate transactions |
| INSEE | insee.fr | Demographics, economy, education |
| ADEME DPE | data.ademe.fr | Energy performance |
| Base Adresse | adresse.data.gouv.fr | Geocoding |
| france-geojson | github.com/gregoiredavid/france-geojson | Boundaries |
| Ville-Idéale | ville-ideale.fr | City reviews |

## Database

PostgreSQL 16. Schema managed by Liquibase (YAML changelogs in `rest-api/src/main/resources/db/changelog/`). JPA/Hibernate runs in `validate` mode — all DDL changes go through Liquibase changelogs.

### Adding a Schema Change

1. Create a new changeset file in `db/changelog/changes/` (e.g. `002-add-foo-table.yaml`)
2. Add the include in `db.changelog-master.yaml`
3. Update the JPA entity in `common/` to match
4. Liquibase applies the migration automatically on startup

## Adding a New Feature

1. Create a new package under the appropriate module (`common/` for entities, `rest-api/` for endpoints, `rest-api/batch/` for importers)
2. Follow package-by-feature structure
3. Use records for all DTOs
4. Add the corresponding API endpoint
5. Add the frontend page/component
6. Update this file if the feature introduces new patterns

## Spark Jobs (backend/spark-jobs)

Spark jobs are standalone JARs — no Spring, no Lombok. They are shaded via `maven-shade-plugin`.

- Follow the same "no comments" rule as the rest of the project
- Use Java records for configuration and value objects
- Use `final` on all local variables; prefer `var` with `final`
- Extract logic into well-named `private static` methods for self-documentation
- Jobs read from CSV/JDBC and write aggregated results to PostgreSQL
- Submit via `spark-submit` (see `docs/architecture.md` for the command)

## Agent-First Development

Treat Copilot like a new team member: give it context, review its work, and keep instructions up to date.

### Recommended Periodic Prompts

Run these regularly (weekly or after major features) to maintain code health:

- `/plan Review the code for missing tests, broken tests, and dead code`
- `/plan Review for duplication or abstraction opportunities`
- `/plan Review documentation and code to identify gaps, update copilot-instructions.md`

After any major feature, run a cleanup pass combining all three prompts to catch regressions, remove dead code, and keep documentation in sync.
