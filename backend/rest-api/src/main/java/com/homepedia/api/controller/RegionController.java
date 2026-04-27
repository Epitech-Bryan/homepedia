package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.REGIONS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Region.BY_CODE;

import com.homepedia.api.service.RegionService;
import com.homepedia.common.region.RegionSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Regions", description = "French administrative regions")
@RestController
@RequestMapping(REGIONS)
@RequiredArgsConstructor
public class RegionController {

	private final RegionService regionService;

	@Operation(summary = "List all regions")
	@GetMapping
	public List<RegionSummary> findAll() {
		return regionService.findAll();
	}

	@Operation(summary = "Get a region by its code")
	@GetMapping(BY_CODE)
	public ResponseEntity<RegionSummary> findByCode(@PathVariable final String code) {
		return regionService.findByCode(code).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}
}
