# Data cleaning methodology

How each source is ingested, normalised, filtered and joined. Every dataset
lands in either PostgreSQL (tabular) or MongoDB (non-tabular reviews), keyed on
INSEE / ISO codes so cross-source joins are exact.

## Sources

| Source | Data | Store |
|---|---|---|
| DVF (data.gouv `geo-dvf`) | Real-estate transactions | Postgres `transactions` (partitioned by year) |
| INSEE / geo.api.gouv.fr | Regions, departments, communes, municipal arrondissements + boundaries | Postgres `regions`/`departments`/`cities`, `geo_boundaries` |
| ADEME DPE | Energy performance diagnostics | Postgres `indicators` (GES) |
| INSEE Filosofi | Income / poverty / inequality at IRIS | Postgres `indicators` |
| GADM 4.1 + Natural Earth | World country / admin-1 / admin-2 / admin-3 boundaries | classpath GeoJSON → `world.mbtiles` |
| Statbel + Wikidata | Belgium provinces / communes (population, area) | classpath GeoJSON |
| Reviews (scrape) | Free-text opinions | MongoDB `city_reviews` |

## DVF (transactions)

Raw `full.csv(.gz)` is streamed (GZIP + univocity CSV, no intermediate DTO) and
written with a bulk `COPY` into a shadow partition, then `ATTACH PARTITION`
atomically per year.

Cleaning / business rules (applied in `CityDvfStatsAggregator` when building the
`city_dvf_yearly_stats` pre-aggregate):

- **Type filter**: only `MAISON` and `APPARTEMENT` (houses/flats) — land,
  garages, commercial lots are excluded from price stats.
- **Range filters**: built surface 9–1000 m², price 10 k–5 M € — drops obvious
  outliers and data-entry errors.
- **Deduplication by `mutation_id`**: one sale (mutation) can appear on several
  rows (multiple lots/parcels); counting rows would double-count. The aggregate
  dedups on `mutation_id`.
- **Surface-weighted €/m²**: computed as `SUM(price) / SUM(surface)`, not an
  average of per-row ratios, so large transactions are weighted correctly.
- **Geolocation**: latitude/longitude are taken directly from the per-mutation
  coordinates `geo-dvf` ships (columns 38/39) at import time; the BAN geocoder is
  only a fallback for rows without coordinates.

### Known source exclusions (not bugs)

- **Alsace-Moselle — Moselle (57), Bas-Rhin (67), Haut-Rhin (68)**: these three
  departments use the *livre foncier* (a local land-registry regime inherited
  from German law), **not** the cadastre that feeds DVF. They are **absent from
  the DVF source nationwide** — 0 transactions, while the neighbouring
  Meurthe-et-Moselle (54) has the expected volume. Nothing to import; there is no
  DVF data to clean.
- **Mayotte (976)**: not covered by the DVF dataset.
- **Paris / Lyon / Marseille**: DVF codes these cities' transactions by *municipal
  arrondissement* (`75101…75120`, `13201…13216`, `69381…69389`), not by the
  parent commune. The arrondissements are imported into `cities` (see below) so
  the transactions link and aggregate correctly.

The map surfaces the first two exclusions with an in-app note when a DVF metric
(price, €/m², transactions) is selected, so the grey areas read as "no source"
rather than a defect.

## INSEE / geographic reference

- Communes are fetched from geo.api.gouv.fr and normalised to
  `{insee_code, name, postal_code, department_code, population, area, lat, lon}`.
- **Municipal arrondissements** for departments 75/13/69 are fetched separately
  (`type=arrondissement-municipal`) and upserted into `cities`, so DVF
  transactions coded by arrondissement link to a real city row.
- Department / region population and area are **recomputed** from their communes,
  **excluding the municipal arrondissements** (`75101…75120`, `13201…13216`,
  `69381…69389`) to avoid double-counting the parent city's population.

## DPE (pollution / GES)

ADEME per-address diagnostics are reduced to a per-commune GES score 1–7 (1 =
mostly class A, 7 = mostly class G), weighted by the number of diagnostics per
class. DPE is an energy dataset, not a land registry, so it **does** cover
Alsace-Moselle — only the DVF price/transaction metrics are missing there.

## Filosofi (IRIS indicators)

Filosofi CSV (~50 k IRIS × ~30 columns) is filtered to the indicators actually
used (median income, poverty rate, Gini, …) and inserted into `indicators` at
`geographic_level = 'IRIS'`.

## World boundaries (GADM / Natural Earth / Belgium)

- Country / admin-1 / admin-2 / admin-3 GeoJSON is trimmed to
  `{code, name, country}` plus baked metrics (population, area, GDP) under
  conventional keys, so the choropleth reads tile properties directly.
- **France and Belgium are excluded from the GADM world layers** — France is
  served by the higher-resolution commune pipeline, Belgium by its dedicated
  Statbel/GADM file. Belgium is **browse-only**: boundaries + population/density,
  no DVF/DPE (French-only sources).

## Reviews (non-tabular)

Scraped review text is stored in MongoDB with a French text index. Sentiment
analysis classifies each review; the word cloud tokenises the corpus and filters
stopwords to surface the salient terms per city.

## Standardisation summary

- All administrative entities are keyed on **INSEE codes** (France) or **ISO /
  GADM codes** (world); joins across DVF, INSEE, DPE and Filosofi are exact code
  matches.
- Numeric fields are coerced to typed columns (no string-typed numbers); missing
  values are stored as `NULL` and rendered as a neutral "no data" fill rather
  than `0`, so absence is never confused with a real zero.
