package com.homepedia.common.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionDetail(Long id, LocalDate mutationDate, String mutationNature, BigDecimal propertyValue,
		String streetNumber, String streetType, String postalCode, String cityName, String cityInseeCode,
		String departmentCode, PropertyType propertyType, Double builtSurface, Integer roomCount, Double landSurface,
		String section, String planNumber, Integer lotCount, Double pricePerSqm) {
}
