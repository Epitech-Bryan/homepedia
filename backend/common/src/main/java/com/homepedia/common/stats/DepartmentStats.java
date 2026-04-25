package com.homepedia.common.stats;

public record DepartmentStats(String code, String name, String regionCode, Long population, Double area,
		Long transactionCount, Double averagePrice, Double averagePricePerSqm) {
}
