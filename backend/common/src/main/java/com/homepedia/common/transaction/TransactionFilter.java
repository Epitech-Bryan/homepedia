package com.homepedia.common.transaction;

import java.math.BigDecimal;

public record TransactionFilter(String cityInseeCode, String departmentCode, String regionCode, Integer year,
		BigDecimal minPrice, BigDecimal maxPrice, PropertyType propertyType, Integer minRooms, Double minSurface) {
}
