package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.GEO;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Geo.GEO_DEPARTMENTS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Geo.GEO_REGIONS;
import static com.homepedia.common.indicator.GeographicLevel.DEPARTMENT;
import static com.homepedia.common.indicator.GeographicLevel.REGION;

import com.homepedia.api.service.CountryGeoService;
import com.homepedia.api.service.GeoService;
import com.homepedia.common.geo.dto.FeatureCollection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
	private final CountryGeoService countryGeoService;

	@Operation(summary = "Country boundaries (world view)", description = "Natural Earth Admin 0 boundaries for every country (177), trimmed to ISO_A3 + name + population + GDP + continent. Used by the map at world-level zoom before falling back to French regions.")
	@GetMapping(value = "/countries", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getCountryBoundaries() {
		return ResponseEntity.ok(countryGeoService.getCountriesGeoJson());
	}

	@Operation(summary = "Belgium provinces", description = "GADM 4.1 boundaries for the 10 Belgian provinces + the Brussels-Capital Region, with Statbel population + area baked into the properties. Browse-only — no DVF/DPE coverage for Belgium yet.")
	@GetMapping(value = "/belgium/provinces", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getBelgiumProvinces() {
		return ResponseEntity.ok(countryGeoService.getBelgiumProvincesGeoJson());
	}

	@Operation(summary = "Region boundaries", description = "GeoJSON FeatureCollection of all French regions")
	@GetMapping(GEO_REGIONS)
	public ResponseEntity<FeatureCollection> getRegionBoundaries() {
		return ResponseEntity.ok(geoService.findBoundariesByLevel(REGION));
	}

	@Operation(summary = "Department boundaries", description = "GeoJSON FeatureCollection of departments, optionally filtered by region")
	@GetMapping(GEO_DEPARTMENTS)
	public ResponseEntity<FeatureCollection> getDepartmentBoundaries(
			@Parameter(description = "Region code to filter by") @RequestParam(required = false) final String regionCode) {
		return ResponseEntity.ok(StringUtils.isNotBlank(regionCode)
				? geoService.findDepartmentBoundariesByRegion(regionCode)
				: geoService.findBoundariesByLevel(DEPARTMENT));
	}
}
