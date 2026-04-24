package com.homepedia.api.service;

import com.homepedia.common.department.Department;
import com.homepedia.common.department.DepartmentRepository;
import com.homepedia.common.geo.GeoJsonBoundary;
import com.homepedia.common.geo.GeoJsonBoundaryRepository;
import com.homepedia.common.indicator.GeographicLevel;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.homepedia.common.indicator.GeographicLevel.DEPARTMENT;
import static java.util.stream.Collectors.toSet;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GeoService {

	private final GeoJsonBoundaryRepository geoJsonBoundaryRepository;
	private final DepartmentRepository departmentRepository;

	public String findBoundariesByLevel(GeographicLevel level) {
		final var boundaries = geoJsonBoundaryRepository.findByGeographicLevel(level);
		return toFeatureCollection(boundaries);
	}

	public String findDepartmentBoundariesByRegion(String regionCode) {
		final var departmentCodes = departmentRepository.findByRegionCode(regionCode).stream()
                .map(Department::getCode)
				.collect(toSet());

		final var boundaries = geoJsonBoundaryRepository.findByGeographicLevel(DEPARTMENT).stream()
				.filter(geoJsonBoundary -> departmentCodes.contains(geoJsonBoundary.getGeographicCode()))
                .toList();

		return toFeatureCollection(boundaries);
	}

	public Optional<String> findBoundary(GeographicLevel level, String code) {
		return geoJsonBoundaryRepository.findByGeographicLevelAndGeographicCode(level, code).map(this::toFeature);
	}

	private String toFeatureCollection(List<GeoJsonBoundary> boundaries) {
		final var features = boundaries.stream().map(this::toFeature).collect(Collectors.joining(","));
		return "{\"type\":\"FeatureCollection\",\"features\":[" + features + "]}";
	}

	private String toFeature(GeoJsonBoundary boundary) {
		return "{\"type\":\"Feature\",\"properties\":{\"code\":\"" + boundary.getGeographicCode() + "\",\"nom\":\""
				+ escapeJson(boundary.getName()) + "\",\"level\":\"" + boundary.getGeographicLevel().name()
				+ "\"},\"geometry\":" + boundary.getGeometry() + "}";
	}

	private static String escapeJson(String value) {
		if (StringUtils.isBlank(value)) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
