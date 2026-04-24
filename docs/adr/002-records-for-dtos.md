# ADR-002: Records for All DTOs

## Status

Accepted

## Context

Java 21 provides records as immutable data carriers with built-in equals/hashCode/toString.

## Decision

All DTOs, projections, filter objects, and API response wrappers are Java records. JPA entities remain classes (records cannot be JPA entities due to no-arg constructor requirements).

## Consequences

- Immutable by default — no accidental mutation
- Less boilerplate — no getters/setters/equals/hashCode
- Clear distinction: entity (mutable, JPA-managed) vs record (immutable, data transfer)
