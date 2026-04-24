package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.GEO;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Geo.GEO_DEPARTMENTS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Geo.GEO_REGIONS;
import static com.homepedia.common.indicator.GeographicLevel.DEPARTMENT;
import static com.homepedia.common.indicator.GeographicLevel.REGION;

import com.homepedia.api.service.GeoService;
import com.homepedia.common.geo.dto.FeatureCollection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "GeoJSON", description = "Geographic boundaries for map rendering")
@RestController
@RequestMapping(GEO)
@RequiredArgsConstructor
public class GeoController {

	private final GeoService geoService;

	@Operation(summary = "Region boundaries", description = "GeoJSON FeatureCollection of all French regions")
	@GetMapping(GEO_REGIONS)
	public FeatureCollection getRegionBoundaries() {
		return geoService.findBoundariesByLevel(REGION);
	}

	@Operation(summary = "Department boundaries", description = "GeoJSON FeatureCollection of departments, optionally filtered by region")
	@GetMapping(GEO_DEPARTMENTS)
	public FeatureCollection getDepartmentBoundaries(
			@Parameter(description = "Region code to filter by") @RequestParam(required = false) final String regionCode) {
		return StringUtils.isNotBlank(regionCode)
				? geoService.findDepartmentBoundariesByRegion(regionCode)
				: geoService.findBoundariesByLevel(DEPARTMENT);
	}
}
