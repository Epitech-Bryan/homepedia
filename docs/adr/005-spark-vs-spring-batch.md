# ADR-005: Spark vs Spring Batch

## Status

Accepted

## Context

The project has 8+ Spring Batch ETL jobs importing data from various sources (INSEE, DPE, GeoJSON, Health, Indicators, Reviews) into PostgreSQL, and 1 Spark job for DVF aggregation. A colleague suggested migrating all batch jobs to Spark for consistency.

## Decision

Use Spark ONLY for heavy aggregation/transformation over large datasets (>1M rows, compute-heavy). Keep Spring Batch for I/O-bound ETL that imports rows into PostgreSQL.

- **Spark**: DVF transaction aggregation (millions of rows, complex groupBy/window operations)
- **Spring Batch**: All row-level imports (INSEE ~35K rows, DPE ~100K, GeoJSON ~120 boundaries, Health ~50K, Indicators ~10-50K, Reviews variable)

## Rationale

- Spark overhead (cluster setup, serialization, shuffle) is wasteful for <100K row imports
- Spring Batch is simpler, well-integrated with the Spring ecosystem, and sufficient for row-level ETL
- Spring Batch jobs benefit from shared Spring context (JPA entities, DataSource, transaction management)
- Spark jobs are standalone JARs with no Spring dependency, adding operational complexity

## Consequences

- DVF aggregation stays in Spark (`backend/spark-jobs/`)
- All other imports remain as Spring Batch jobs (`backend/rest-api/src/main/java/com/homepedia/api/batch/`)
- Future: if the DPE dataset grows beyond 1M rows, consider adding a Spark job for DPE aggregations
- New data sources default to Spring Batch unless they involve heavy aggregation over large datasets
