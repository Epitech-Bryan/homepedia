package com.homepedia.common.stats;

/**
 * One point on the per-commune price-per-m² timeline.
 * {@code averagePricePerSqm} is computed at query time from
 * {@code totalPrice / totalResidentialSurface} (surface-weighted), same formula
 * as the yearly stats endpoint — quarters with no MAISON/APPARTEMENT sales just
 * expose the transaction count and leave the price null.
 */
public record QuarterlyPricePoint(int year, int quarter, long transactionCount, Double averagePricePerSqm) {
}
