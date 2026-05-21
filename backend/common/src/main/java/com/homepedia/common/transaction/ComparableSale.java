package com.homepedia.common.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One nearest-comparable for a given transaction, returned by {@code GET
 * /transactions/{id}/comparable-sales}. The popup on the map marker renders
 * these as a short list ordered by {@code similarityRank} (1 = closest match).
 *
 * <p>
 * Backed by the {@code comparable_transactions} pre-aggregate (changeset 015)
 * which a future Spark clustering job will populate; the wire format is fixed
 * now so the frontend can be developed against the final shape.
 */
public record ComparableSale(int similarityRank, Long comparableId, LocalDate mutationDate, BigDecimal propertyValue,
		PropertyType propertyType, Double builtSurface, Integer roomCount, Double latitude, Double longitude,
		Integer distanceM, BigDecimal priceDeltaPct) {
}
