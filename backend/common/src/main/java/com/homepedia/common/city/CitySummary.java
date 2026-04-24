package com.homepedia.common.city;

public record CitySummary(String inseeCode, String name, String postalCode, String departmentCode,
		String departmentName, Long population, Double latitude, Double longitude) {
}
