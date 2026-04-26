package com.homepedia.common.stats;

public record CityStats(String code, String name, String departmentCode, Long population, Double area,
		Long transactionCount, Double averagePrice, Double averagePricePerSqm) {
}
