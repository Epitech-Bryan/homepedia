# HomePedia

Interactive analysis of the French housing market: real-estate prices, demographics, indicators (energy, health, education, environment, infrastructure, economy) and city-level reviews with sentiment analysis. Multi-level navigation (region → department → city) with interactive maps.

## Stack résumée

- **Front** : React 19 + Vite + Leaflet (choropleth, bubbles, heatmap, city markers)
- **API** : Spring Boot 3.5 + Spring Batch + SSE
- **Storage** : PostgreSQL 16 (relationnel) + MongoDB 7 (reviews textuelles)
- **Big Data** : Apache Spark 3.5 (cluster Docker Compose) pour l'agrégation DVF
- **AI** : sentiment analysis sur les reviews

Voir [`docs/architecture.md`](docs/architecture.md) pour l'archi détaillée, le schéma DB, la méthodologie de cleaning, et l'implémentation IA.

## Lancer en local

```bash
# Toute la stack (Postgres + Mongo + Spark + backend + webapp + Traefik)
docker compose up -d

# Front : http://homepedia.localhost
# Spark UI : http://localhost:8088
# API : http://homepedia.localhost/api
# Swagger : http://homepedia.localhost/api/swagger-ui.html
```

Pour activer les imports au boot, exporter avant `docker compose up` :

```bash
HOMEPEDIA_BATCH_STARTUP_ENABLED=true
HOMEPEDIA_DPE_ENABLED=true
HOMEPEDIA_HEALTH_ENABLED=true
IMPORT_REVIEWS_ENABLED=true
HOMEPEDIA_DVF_ZIP_PATH=/tmp/dvf.csv.gz   # tout chemin non vide déclenche le job DVF
```

## Tests / build

```bash
# Backend
cd backend && mvn package

# Webapp
cd webapp && pnpm install && pnpm build

# Spotless / Prettier
cd backend && mvn spotless:check
cd webapp && pnpm lint && pnpm format:check
```

## Pour aller plus loin

- [`docs/architecture.md`](docs/architecture.md) — schéma DB, sources de données, cleaning, AI, Spark, real-time
- [`backend/spark-jobs/`](backend/spark-jobs/) — code et instructions du job d'agrégation Spark
- [`compose.yml`](compose.yml) — orchestration locale complète (PG + Mongo + Spark master/worker + API + webapp)
- [`renovate.json`](renovate.json) — politique de bumps (Spring Boot < 3.6, springdoc < 3.0)
