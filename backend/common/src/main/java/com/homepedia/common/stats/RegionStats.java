package com.homepedia.common.stats;

public record RegionStats(
		String code,
		String name,
		Long population,
		Double area,
		Long transactionCount,
		Double averagePrice,
		Double averagePricePerSqm) {
}
