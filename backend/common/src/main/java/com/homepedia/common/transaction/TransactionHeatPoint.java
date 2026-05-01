package com.homepedia.common.transaction;

/**
 * Single point used to feed the map heatmap layer. {@code value} is the
 * metric the heatmap is colouring (avg price/m², transaction count, etc.) at
 * that location — the frontend normalises across the response to the
 * choropleth scale.
 */
public record TransactionHeatPoint(double latitude, double longitude, double value) {
}
