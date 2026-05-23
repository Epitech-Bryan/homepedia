package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.GEO;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Geo.GEO_DEPARTMENTS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Geo.GEO_REGIONS;
import static com.homepedia.common.indicator.GeographicLevel.DEPARTMENT;
import static com.homepedia.common.indicator.GeographicLevel.REGION;

import com.homepedia.api.service.CountryGeoService;
import com.homepedia.api.service.GeoService;
import com.homepedia.api.service.OsmPoiService;
import com.homepedia.common.geo.dto.FeatureCollection;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
	private final OsmPoiService osmPoiService;

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

	@Operation(summary = "Belgium municipalities", description = "GADM 4.1 boundaries for the 581 Belgian communes with Wikidata-sourced NIS code, population and surface baked into the properties (98 % coverage). Lets the choropleth render population / density at commune zoom in Belgium, mirroring the FR commune layer.")
	@GetMapping(value = "/belgium/municipalities", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getBelgiumMunicipalities() {
		return ResponseEntity.ok(countryGeoService.getBelgiumMunicipalitiesGeoJson());
	}

	@Operation(summary = "World admin-1 (states/provinces/regions)", description = "Simplified GADM 4.1 boundaries (~5 km tolerance) for ~38 EU + G20 countries' first-level subdivisions. Excludes France (covered by /geo/regions) and Belgium (covered by /geo/belgium/provinces). Used by the map at zoom 5-7 to render foreign countries' admin-1 alongside French regions.")
	@GetMapping(value = "/world/admin1", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getWorldAdmin1() {
		return ResponseEntity.ok(countryGeoService.getWorldAdmin1GeoJson());
	}

	@Operation(summary = "World admin-2 (counties/districts/Kreise)", description = "Simplified GADM 4.1 admin-2 boundaries (~3 km tolerance) for ~18 European countries — DEU Kreise, GBR districts, ITA province, etc. Lets the map render sub-region detail past zoom 7 outside France. Excludes FR/BE (covered by their own dedicated endpoints).")
	@GetMapping(value = "/world/admin2", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getWorldAdmin2() {
		return ResponseEntity.ok(countryGeoService.getWorldAdmin2GeoJson());
	}

	@Operation(summary = "World admin-1 region detail", description = "Per-feature detail for one admin-1 region keyed by its GADM GID_1 (e.g. DEU.2_1). Returns name, country, baked Wikidata metrics, bounding box and a count of admin-2 children. 404 when the code is unknown.")
	@GetMapping(value = "/world/admin1/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<CountryGeoService.WorldAdmin1Detail> getWorldAdmin1Detail(@PathVariable final String code) {
		final var detail = countryGeoService.getWorldAdmin1Detail(code);
		if (detail == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(detail);
	}

	@Operation(summary = "World admin-1 search", description = "Substring match against the world admin-1 region names (~3k entries). Case- and accent-insensitive. Results sorted by population descending so the populous regions surface first.")
	@GetMapping(value = "/world/search", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<CountryGeoService.WorldSearchResult>> searchWorld(
			@org.springframework.web.bind.annotation.RequestParam("q") final String query,
			@org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "10") final int limit) {
		return ResponseEntity.ok(countryGeoService.searchWorldAdmin1(query, Math.min(Math.max(limit, 1), 50)));
	}

	@Operation(summary = "OSM POIs in bbox", description = "Proxies a single Overpass query for museums, stations, schools, hospitals, parks and attractions within the bbox. Each (rounded) bbox is cached 7 days in Redis so panning around doesn't hammer the public Overpass server. Returns an empty list on timeout or bbox > 10°×10°.")
	@GetMapping(value = "/pois", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<OsmPoiService.PoiDto>> getPois(
			@org.springframework.web.bind.annotation.RequestParam("south") final double south,
			@org.springframework.web.bind.annotation.RequestParam("west") final double west,
			@org.springframework.web.bind.annotation.RequestParam("north") final double north,
			@org.springframework.web.bind.annotation.RequestParam("east") final double east) {
		return ResponseEntity.ok(osmPoiService.fetchPois(south, west, north, east));
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

	@Operation(summary = "IRIS boundaries under a commune", description = "GeoJSON FeatureCollection of every IRIS polygon whose 9-char code starts with the given 5-char commune INSEE. Empty FC if the Filosofi IRIS dataset hasn't been imported yet — the frontend can plug it into a Leaflet layer unconditionally.")
	@GetMapping(value = "/iris/{communeInseeCode}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<FeatureCollection> getIrisBoundariesByCommune(
			@Parameter(description = "5-char INSEE code of the parent commune") @PathVariable final String communeInseeCode) {
		return ResponseEntity.ok(geoService.findIrisBoundariesByCommune(communeInseeCode));
	}
}
