# Homepedia — GitHub Copilot Agent Instructions

See the root `copilot-instructions.md` for full project conventions.

## Quick Reference

- **Java 21** · Spring Boot 3.5 · PostgreSQL 16 · React 19 · Vite · Tailwind
- **Package-by-feature** — never package-by-layer
- **Records** for all DTOs, projections, filters, responses
- **Optional** for nullable returns — never return null
- **No comments** — self-documenting code via clear naming
- **No hidden coupling** — cross-feature dependencies go through services
- **Sealed interfaces** for polymorphic types (indicators, etc.)
- **Error handling** via `@RestControllerAdvice` returning `ErrorResponse` records
- **Frontend**: TanStack Query for data, React Router for nav, typed API layer
- **Spark jobs**: standalone JARs (no Spring/Lombok), heavy aggregation only (see ADR-005)
- **Spring Batch**: I/O-bound ETL imports (<100K rows) inside `rest-api/batch/`
- **Agent-first**: run periodic review prompts weekly (see root `copilot-instructions.md`)

## When Making Changes

1. Follow existing patterns in the same package
2. Use records for any new DTO or value object
3. Do not add Javadoc or inline comments
4. Do not create package-by-layer structures
5. Run `mvn spotless:check` before committing backend changes
6. Run `pnpm lint` before committing frontend changes
7. Update `copilot-instructions.md` if introducing new patterns
