# ADR-004: PostgreSQL as Primary Database

## Status

Accepted

## Context

We need a database supporting relational queries, spatial data (PostGIS), full-text search, and JSON storage.

## Decision

PostgreSQL 16 with PostGIS extension serves as the single primary database. We use:

- Relational tables for structured data (transactions, indicators, demographics)
- PostGIS geometry columns for geographic boundaries and spatial queries
- `tsvector` / full-text search for city review text queries
- JSONB columns where flexible schema is needed (raw indicator data)

## Consequences

- Single database simplifies operations and deployment
- PostGIS avoids the need for a separate spatial database
- Full-text search avoids the need for Elasticsearch for basic text queries
- If review volume grows very large, we may revisit adding a dedicated search engine
