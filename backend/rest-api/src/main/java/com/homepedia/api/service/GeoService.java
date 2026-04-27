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

	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'level:' + #level")
	public FeatureCollection findBoundariesByLevel(final GeographicLevel level) {
		final var boundaries = geoJsonBoundaryRepository.findByGeographicLevel(level);
		return GeoMapper.INSTANCE.convertToFeatureCollection(boundaries);
	}

	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'depts-of-region:' + #regionCode")
	public FeatureCollection findDepartmentBoundariesByRegion(final String regionCode) {
		final var departmentCodes = departmentRepository.findByRegionCode(regionCode).stream().map(Department::getCode)
				.collect(toSet());

		final var boundaries = geoJsonBoundaryRepository.findByGeographicLevel(GeographicLevel.DEPARTMENT).stream()
				.filter(b -> departmentCodes.contains(b.getGeographicCode())).toList();

		return GeoMapper.INSTANCE.convertToFeatureCollection(boundaries);
	}

    @Cacheable(value = CacheConfig.CACHE_GEO, key = "'boundary:' + #level + ':' + #code", unless = "#result == null")
    public Optional<Feature> findBoundary(final GeographicLevel level, final String code) {
        return geoJsonBoundaryRepository.findByGeographicLevelAndGeographicCode(level, code)
                .map(GeoMapper.INSTANCE::convertToFeature);
    }
}