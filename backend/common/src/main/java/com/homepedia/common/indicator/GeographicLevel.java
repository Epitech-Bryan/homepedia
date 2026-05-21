package com.homepedia.common.indicator;

/**
 * Geographic granularity for indicators and boundaries. The first three tiers —
 * {@code REGION}, {@code DEPARTMENT}, {@code CITY} — match the French INSEE
 * hierarchy. The remaining tiers extend the model to other countries:
 * {@code COUNTRY} for ISO 3166-1 alpha-3 codes (top-level macro indicators) and
 * {@code NUTS1}/{@code NUTS2}/{@code NUTS3} for the European NUTS
 * classification (covers all EU member states plus the UK via the legacy NUTS
 * code set Eurostat still publishes).
 *
 * <p>
 * The varchar(20) column in the schema already accommodates any name in this
 * enum, so adding new tiers does not require a migration.
 */
public enum GeographicLevel {
	REGION, DEPARTMENT, CITY, IRIS, COUNTRY, NUTS1, NUTS2, NUTS3
}
