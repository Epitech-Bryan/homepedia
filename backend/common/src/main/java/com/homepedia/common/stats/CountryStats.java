package com.homepedia.common.stats;

/**
 * National-level aggregate. Only pollution is derived server-side today (the
 * weighted GES score over every commune); other national figures come from the
 * existing transaction-stats and country-overlay endpoints. Kept as a record so
 * further national aggregates can be added without breaking the API shape.
 */
public record CountryStats(Double pollutionScore) {
}
