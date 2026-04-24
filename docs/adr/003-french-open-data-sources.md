# ADR-003: French Government Open Data as Primary Source

## Status

Accepted

## Context

The project requires comprehensive housing data across French regions, departments, and cities.

## Decision

We use French government open data platforms as primary sources:

- **DVF** (data.gouv.fr) for real estate transaction history
- **INSEE** for demographics, economy, and education statistics
- **ADEME** for energy performance diagnostics (DPE)
- **Base Adresse Nationale** for geocoding
- **france-geojson** for administrative boundary geometries
- **Ville-Idéale** for user-generated city reviews (text/sentiment)

## Consequences

- Data is free, authoritative, and regularly updated
- Standardized INSEE codes serve as join keys across datasets
- Large volumes (DVF has millions of rows) require batch processing
- Text data from reviews requires NLP/sentiment analysis
