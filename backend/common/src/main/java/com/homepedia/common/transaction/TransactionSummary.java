package com.homepedia.common.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionSummary(Long id, LocalDate mutationDate, String mutationNature, BigDecimal propertyValue,
		String postalCode, String cityName, String cityInseeCode, PropertyType propertyType, Double builtSurface,
		Integer roomCount, Double landSurface) {
}
