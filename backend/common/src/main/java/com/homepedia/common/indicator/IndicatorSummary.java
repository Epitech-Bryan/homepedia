package com.homepedia.common.indicator;

public record IndicatorSummary(Long id, IndicatorCategory category, String label, Double value, String unit,
		Integer year) {
}
