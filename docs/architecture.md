# HomePedia — Architecture

## Vue d'ensemble

```
                    ┌──────────────────┐
                    │   webapp (Vite)  │
                    │  React + Leaflet │
                    └────────┬─────────┘
                             │ HTTP / SSE
                    ┌────────▼─────────┐
                    │   Traefik (80)   │
                    └────────┬─────────┘
                             │
                  ┌──────────▼──────────┐
                  │  rest-api (Spring)  │
                  │  Web + Batch + AI   │
                  └─┬─────────┬───────┬─┘
       JDBC   ┌─────┘         │       └─────┐  Mongo
              │           Spark submit       │
       ┌──────▼─────┐  ┌──────▼─────┐  ┌────▼─────┐
       │ PostgreSQL │  │   Spark    │  │  MongoDB  │
       │  (geo +    │  │  master +  │  │ (reviews  │
       │   DVF +    │  │   workers  │  │  + texte  │
       │ indicators)│  │            │  │   libre)  │
       └────────────┘  └────────────┘  └───────────┘
```

## Stack

| Couche | Techno | Rôle |
|---|---|---|
| Front | React 19, Vite 6, react-leaflet, leaflet.heat, TanStack Query, shadcn/ui | UI interactive, cartes choropleth/bubble/heat, recherche async |
| API | Spring Boot 3.5, Spring MVC, HATEOAS, springdoc, SSE | REST + temps réel |
| Batch | Spring Batch 5 + tasklets | Imports tabulaires (INSEE, DVF, DPE, Health, indicators) |
| AI | Service `SentimentAnalysisService` (intégration locale) | Analyse de sentiment des reviews |
| SQL | PostgreSQL 16 + Liquibase | Données géo, transactions, indicators |
| NoSQL | MongoDB 7 | Reviews textuelles |
| Big data | Apache Spark 3.5 (master + worker) | Agrégations DVF lourdes |
| Déploiement | Docker / Docker Compose / Kubernetes | Stack reproductible |
| CI/CD | GitLab CI + FerrFlow + Buildah | Validation, release, publish images |

## Modules backend

```
backend/
├── pom.xml              # multi-module parent
├── common/              # entités JPA, documents Mongo, repositories partagés
├── rest-api/            # contrôleurs, services, batch jobs, événements SSE
└── spark-jobs/          # jobs Spark autonomes (shaded jar)
```

Le module `common` est consommé à la fois par `rest-api` (pour CRUD via JPA/Mongo) et par `spark-jobs` (uniquement pour la dépendance JDBC PostgreSQL).

## Bases de données

### PostgreSQL — données structurées

Schéma piloté par Liquibase (`backend/rest-api/src/main/resources/db/changelog/`).

| Table | PK | Description |
|---|---|---|
| `regions` | `code` | 18 régions françaises (INSEE) |
| `departments` | `code` | 101 départements |
| `cities` | `insee_code` | ~35 000 communes avec lat/lon |
| `transactions` | `id` (auto) | DVF — ~10 M lignes par millésime |
| `geo_json_boundaries` | `code + level` | GeoJSON brut (régions, départements) |
| `indicators` | `id` (auto) | Faits multi-domaines (DPE, santé, économie, éducation, environnement, infra) |
| `dept_dvf_stats` | `department_code` | Sortie du job Spark (agrégats DVF) |
| `BATCH_*` | — | Métadonnées Spring Batch, créées par le changeset `002-spring-batch-schema` qui charge `schema-postgresql.sql` du jar `spring-batch-core` |

### MongoDB — données textuelles

| Collection | Document | Pourquoi NoSQL |
|---|---|---|
| `city_reviews` | `CityReview` (id, content, sentimentScore, sentimentLabel, publishedAt, author, rating) | Texte libre + métadonnées variables. Recherche full-text future possible via `$text`. |

Le mix relationnel + non-relationnel répond à l'exigence de la spec ("relational AND non-relational").

## Pipeline de données

```
INSEE API ─────► InseeImportService ────► PG (regions/departments/cities)
data.gouv ─────► DvfImportService ──────► PG (transactions)
ADEME / Ameli ─► DPE/Health services ───► PG (indicators)
CSV utilisateur► Generic indicator svc ─► PG (indicators)
Scraping/gen ──► ReviewScraperService ──► Mongo (city_reviews)
       │
       └──► SentimentAnalysisService (annoté avant écriture)
```

### Cleaning — méthodologie

| Source | Étapes appliquées |
|---|---|
| INSEE communes | Filtrage des entrées sans `code` ni `nom` ; conservation du 1er code postal seulement ; coordonnées extraites de `centre.coordinates` ; rejet des communes orphelines (département inconnu) |
| DVF | Suppression des transactions avec `valeur_fonciere` nulle ou ≤ 0 ; agrégation €/m² uniquement quand `surface_reelle_bati > 0` ; ignorer les multi-lots où la valeur n'est pas attribuable |
| DPE | Agrégation par commune : `% de logements par classe DPE A→G` ; quotient sur le total de logements pour neutraliser le biais de taille |
| Health | Parsing CSV semicolon Ameli ; mapping classes d'âge → label, valeur d'effectif numérique |
| Indicators (generic) | Format normalisé `code_insee,year,value,label,unit` ; lignes invalides rejetées en silence avec compteur de skipped |
| Reviews | Sentiment scoré entre -1 et 1, label dérivé (POSITIVE/NEGATIVE/NEUTRAL) ; persistance Mongo |

Les agrégats `population` et `area` au niveau département/région sont calculés par `SUM` natif PostgreSQL après l'import des communes (cf. `RegionRepository.recomputeAggregates()`).

## Spark — Big Data

### Pourquoi

Le DVF complet pèse ~3 GB décompressé (~10 M lignes). Le charger via Hibernate marche mais est lent et single-thread. Spark distribue la charge.

### Le job

`backend/spark-jobs/src/main/java/com/homepedia/spark/DvfAggregateJob.java` :

1. Lit le CSV DVF brut
2. Joint avec `cities` (PG) pour rattacher chaque transaction à un département
3. Agrège par département : count, AVG price, AVG €/m², median price (`percentile_approx`)
4. Écrit dans `dept_dvf_stats` (overwrite)

### Lancement

```bash
mvn -pl spark-jobs package
docker compose up -d spark-master spark-worker
docker compose exec spark-master \
  spark-submit \
    --class com.homepedia.spark.DvfAggregateJob \
    --master spark://spark-master:7077 \
    --jars /opt/spark/jars/postgresql.jar \
    /jobs/spark-jobs-3.11.1-spark.jar \
    --input-path /data/dvf.csv \
    --jdbc-url jdbc:postgresql://db:5432/homepedia \
    --jdbc-user homepedia --jdbc-password homepedia
```

UI Spark : http://localhost:8088

## Real-time

```
BatchScheduler / BatchLauncherRunner
        │  publish(BatchEvent)
        ▼
BatchEventPublisher (in-memory fan-out)
        │  SseEmitter
        ▼
GET /api/events/batch (text/event-stream)
        │
        ▼
useBatchEvents() (EventSource côté React)
        │
        ▼
<BatchEventsBanner /> dans le Layout
```

Événements émis : `STARTING`, `RUNNING`, `COMPLETED`, `FAILED`. La bannière s'affiche dès que le 1er événement arrive et reste visible.

Limitation : la fan-out est in-memory, donc un seul pod. Pour scale horizontalement, brancher sur Redis pub/sub.

## AI — sentiment analysis

Implémentation : `com.homepedia.api.batch.review.SentimentAnalysisService`.

Approche : score continu `[-1, 1]` produit pour chaque review au moment de l'import, puis dérivé en label discret pour faciliter le filtrage côté API/UI.

| Score | Label |
|---|---|
| `≥ 0.2` | POSITIVE |
| `≤ -0.2` | NEGATIVE |
| sinon | NEUTRAL |

Le score et le label sont stockés directement sur le document Mongo `CityReview`. Endpoints exposés :
- `GET /api/cities/{insee}/reviews` — liste paginée
- `GET /api/cities/{insee}/reviews/word-cloud` — fréquences pour le word cloud
- `GET /api/cities/{insee}/reviews/sentiment-stats` — répartition agrégée

## CI/CD

```
push (main, MR)
  ├── lint (spotless + eslint + prettier)
  ├── build (mvn package + pnpm build) ← validation
  ├── release (FerrFlow décide d'un bump si commits sémantiques)
  └── mirror (push vers GitHub)

push (tag webapp-v* | rest-api-v*)
  └── publish (buildah → registry GitLab)
```

Cache CI : `~/.m2/repository` et `.pnpm-store/` pinés sur les lockfiles.

## Variables d'environnement

Voir `compose.yml` pour les valeurs locales. En prod :

```
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
SPRING_DATA_MONGODB_URI=mongodb://.../homepedia

HOMEPEDIA_BATCH_STARTUP_ENABLED=true
HOMEPEDIA_DPE_ENABLED=true
HOMEPEDIA_HEALTH_ENABLED=true
IMPORT_REVIEWS_ENABLED=true
HOMEPEDIA_DVF_ZIP_PATH=/data/dvf.csv.gz
HOMEPEDIA_ECONOMY_ENABLED=true
HOMEPEDIA_ECONOMY_CSV_PATH=/data/economy.csv
# (idem pour education, environment, infrastructure)

HOMEPEDIA_SCHEDULER_ENABLED=true
HOMEPEDIA_SCHEDULER_INSEE_CRON="0 0 3 1 * *"
HOMEPEDIA_SCHEDULER_DVF_CRON="0 0 4 5 * *"
# ... etc.
```

## Limitations connues

- **Spark** est livré comme cluster Docker Compose à 1 master + 1 worker pour la démo. Une vraie infra utiliserait Databricks ou EMR avec ≥ 3 workers.
- **MongoDB** n'a pas encore d'auth configurée en local — à activer via `MONGO_INITDB_ROOT_*` et la connection string en prod.
- **SSE** n'est pas multi-pod (cf. plus haut).
- Le job Spark doit être lancé manuellement (pas de scheduler auto pour l'instant).
