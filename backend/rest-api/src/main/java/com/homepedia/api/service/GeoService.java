package com.homepedia.api.service;

import com.homepedia.api.config.CacheConfig;
import com.homepedia.api.mapper.GeoMapper;
import com.homepedia.common.department.Department;
import com.homepedia.common.department.DepartmentRepository;
import com.homepedia.common.geo.GeoJsonBoundaryRepository;
import com.homepedia.common.geo.dto.Feature;
import com.homepedia.common.geo.dto.FeatureCollection;
import com.homepedia.common.indicator.GeographicLevel;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static java.util.stream.Collectors.toSet;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GeoService {

	private final GeoJsonBoundaryRepository geoJsonBoundaryRepository;
	private final DepartmentRepository departmentRepository;
	private final GeoMapper geoMapper;

	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'level:' + #level")
	public FeatureCollection findBoundariesByLevel(final GeographicLevel level) {
		final var boundaries = geoJsonBoundaryRepository.findByGeographicLevel(level);
		return geoMapper.convertToFeatureCollection(boundaries);
	}

	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'depts-of-region:' + #regionCode")
	public FeatureCollection findDepartmentBoundariesByRegion(final String regionCode) {
		final var departmentCodes = departmentRepository.findByRegionCode(regionCode).stream().map(Department::getCode)
				.collect(toSet());

		final var boundaries = geoJsonBoundaryRepository.findByGeographicLevel(GeographicLevel.DEPARTMENT).stream()
				.filter(b -> departmentCodes.contains(b.getGeographicCode())).toList();

		return geoMapper.convertToFeatureCollection(boundaries);
	}

	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'boundary:' + #level + ':' + #code", unless = "#result == null")
	public Optional<Feature> findBoundary(final GeographicLevel level, final String code) {
		return geoJsonBoundaryRepository.findByGeographicLevelAndGeographicCode(level, code)
				.map(geoMapper::convertToFeature);
	}

	/**
	 * IRIS boundaries that sit under one commune — keyed on the 5-char INSEE prefix
	 * of the 9-char IRIS code (e.g. {@code 75101} → all 992 Paris-1 IRIS rows).
	 * Cached for 24 h (CACHE_GEO) since boundaries don't change between Filosofi
	 * snapshots.
	 *
	 * @see GeoJsonBoundaryRepository#findIrisBoundariesByCommune(String)
	 */
	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'iris-of-commune:' + #communeInseeCode")
	public FeatureCollection findIrisBoundariesByCommune(final String communeInseeCode) {
		final var boundaries = geoJsonBoundaryRepository.findIrisBoundariesByCommune(communeInseeCode + "%");
		return geoMapper.convertToFeatureCollection(boundaries);
	}
}
