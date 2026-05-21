package com.homepedia.common.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lightweight per-transaction payload for the map markers layer. Includes only
 * what the popup renders so a viewport-wide fetch stays small on the wire even
 * at the row cap (a few hundred markers ≈ 30 KB JSON).
 */
public record TransactionMarker(Long id, double latitude, double longitude, LocalDate mutationDate,
		BigDecimal propertyValue, PropertyType propertyType, Double builtSurface, Integer roomCount) {
}
