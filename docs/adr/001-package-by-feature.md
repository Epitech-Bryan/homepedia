# ADR-001: Package-by-Feature Structure

## Status

Accepted

## Context

The backend is a multi-module Maven project. We need a consistent package organization strategy.

## Decision

We use **package-by-feature** rather than package-by-layer. Each domain feature (city, region, transaction, etc.) gets its own package containing the entity, DTO records, controller, service, and repository.

## Consequences

- High cohesion within each feature package
- Low coupling between features (cross-feature access goes through services)
- Easy to navigate — everything for a feature is in one place
- Easy to delete — removing a feature means removing one package
