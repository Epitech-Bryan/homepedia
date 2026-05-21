package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.CITIES;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.City.BY_INSEE_CODE;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.City.IRIS_INDICATORS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.City.PRICE_HISTORY;

import com.homepedia.api.service.CityService;
import com.homepedia.api.service.IndicatorService;
import com.homepedia.common.city.CitySummary;
import com.homepedia.common.indicator.IndicatorSummary;
import com.homepedia.common.stats.QuarterlyPricePoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cities", description = "French communes / cities")
@RestController
@RequestMapping(CITIES)
@RequiredArgsConstructor
public class CityController {

	private final CityService cityService;
	private final IndicatorService indicatorService;
	private final PagedResourcesAssembler<CitySummary> pagedResourcesAssembler;

	@Operation(summary = "Search cities", description = "Paginated list of cities, filterable by department or name query")
	@GetMapping
	public ResponseEntity<PagedModel<EntityModel<CitySummary>>> findAll(
			@Parameter(description = "Department code") @RequestParam(required = false) final String departmentCode,
			@Parameter(description = "Name search query") @RequestParam(required = false) final String query,
			final Pageable pageable) {
		final var page = cityService.findAll(departmentCode, query, pageable);
		return ResponseEntity.ok(pagedResourcesAssembler.toModel(page));
	}

	@Operation(summary = "Get a city by its INSEE code")
	@GetMapping(BY_INSEE_CODE)
	public ResponseEntity<CitySummary> findByInseeCode(@PathVariable final String inseeCode) {
		return cityService.findByInseeCode(inseeCode).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@Operation(summary = "Quarterly price history", description = "Per-quarter price/m² and transaction count for the requested commune over every year the DVF aggregator has populated. Returns an empty array when the commune has no DVF rows.")
	@GetMapping(PRICE_HISTORY)
	public ResponseEntity<List<QuarterlyPricePoint>> priceHistory(@PathVariable final String inseeCode) {
		return ResponseEntity.ok(cityService.priceHistory(inseeCode));
	}

	@Operation(summary = "IRIS-level indicators (Filosofi)", description = "Sub-communal indicators (median income, poverty rate, …) for every IRIS block whose code lives under this commune. Returns an empty array until the INSEE Filosofi importer (issue #10) seeds the indicators table; the endpoint is wired now so the frontend overlay can be developed against the contract.")
	@GetMapping(IRIS_INDICATORS)
	public ResponseEntity<List<IndicatorSummary>> irisIndicators(@PathVariable final String inseeCode) {
		return ResponseEntity.ok(indicatorService.findIrisIndicatorsByCommune(inseeCode));
	}
}
