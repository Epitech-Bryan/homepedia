# Contributing to Homepedia

## Prerequisites

- Java 21 (use SDKMAN: `sdk env install`)
- Maven 3.9+ (use SDKMAN)
- Node.js 20+ / pnpm
- Docker & Docker Compose

## Getting Started

```bash
git clone <repo-url> && cd homepedia
cp .env.example .env
docker compose up -d
```

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(city): add city detail endpoint
fix(dvf-importer): handle missing postal codes
refactor(common): extract indicator sealed interface
```

Scopes: `common`, `rest-api`, `data-pipeline`, `webapp`, `ci`, `docs`

## Adding a New Data Source

1. Create a new package in `backend/data-pipeline/src/main/java/com/homepedia/pipeline/<source>/`
2. Implement an importer class (Spring Batch job or `@Scheduled` task)
3. Add the corresponding JPA entity in `backend/common/`
4. Add the REST endpoint in `backend/rest-api/`
5. Format: `cd backend && mvn spotless:apply`

## Adding a New API Endpoint

1. Create/extend the feature package in `backend/rest-api/src/main/java/com/homepedia/api/<feature>/`
2. Add: Controller → Service → Repository (all in same package)
3. Use records for request/response DTOs
4. Return `Optional` from repository/service, map to 404 in controller

## Adding a New Frontend Page

1. Create the page component in `webapp/src/pages/<Feature>/`
2. Add the API client function in `webapp/src/api/<feature>.ts`
3. Create a TanStack Query hook in `webapp/src/hooks/use<Feature>.ts`
4. Add the route in the router config
5. Lint: `cd webapp && pnpm lint`

## Code Quality

```bash
# Backend — format check
cd backend && mvn spotless:check

# Backend — format apply
cd backend && mvn spotless:apply

# Frontend — lint
cd webapp && pnpm lint

# Frontend — format
cd webapp && pnpm format
```
