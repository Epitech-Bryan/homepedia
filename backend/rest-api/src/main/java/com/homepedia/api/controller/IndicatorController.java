package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.INDICATORS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Indicator.BY_LEVEL_AND_CODE;

import com.homepedia.api.service.IndicatorService;
import com.homepedia.common.indicator.GeographicLevel;
import com.homepedia.common.indicator.IndicatorCategory;
import com.homepedia.common.indicator.IndicatorSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Indicators", description = "Statistical indicators (demographics, economy, health, energy…)")
@RestController
@RequestMapping(INDICATORS)
@RequiredArgsConstructor
public class IndicatorController {

	private final IndicatorService indicatorService;

	@Operation(summary = "Get indicators for a geographic entity", description = "Returns indicators for a region, department, or city, optionally filtered by category")
	@GetMapping(BY_LEVEL_AND_CODE)
	public List<IndicatorSummary> findIndicators(
			@Parameter(description = "Geographic level") @PathVariable final GeographicLevel level,
			@Parameter(description = "Geographic code (region, department, or INSEE)") @PathVariable final String code,
			@Parameter(description = "Indicator category filter") @RequestParam(required = false) final IndicatorCategory category) {
		return indicatorService.findIndicators(level, code, category);
	}
}
