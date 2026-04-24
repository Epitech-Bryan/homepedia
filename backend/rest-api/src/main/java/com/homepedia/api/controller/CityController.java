package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.CITIES;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.City.BY_INSEE_CODE;

import com.homepedia.api.service.CityService;
import com.homepedia.common.city.CitySummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
	private final PagedResourcesAssembler<CitySummary> pagedResourcesAssembler;

	@Operation(summary = "Search cities", description = "Paginated list of cities, filterable by department or name query")
	@GetMapping
	public PagedModel<EntityModel<CitySummary>> findAll(
			@Parameter(description = "Department code") @RequestParam(required = false) final String departmentCode,
			@Parameter(description = "Name search query") @RequestParam(required = false) final String query,
			final Pageable pageable) {
		final var page = cityService.findAll(departmentCode, query, pageable);
		return pagedResourcesAssembler.toModel(page);
	}

	@Operation(summary = "Get a city by its INSEE code")
	@GetMapping(BY_INSEE_CODE)
	public ResponseEntity<CitySummary> findByInseeCode(@PathVariable final String inseeCode) {
		return cityService.findByInseeCode(inseeCode).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}
}
