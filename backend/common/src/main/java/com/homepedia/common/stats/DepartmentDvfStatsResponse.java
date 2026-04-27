package com.homepedia.common.stats;

public record DepartmentDvfStatsResponse(String departmentCode, Long transactionCount, Double avgPrice,
		Double avgPricePerSqm, Double medianPrice) {
}
