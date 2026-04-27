package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.STATS;

import com.homepedia.api.constant.HomepediaConstant;
import com.homepedia.api.service.StatsService;
import com.homepedia.common.stats.CityStats;
import com.homepedia.common.stats.DepartmentDvfStatsResponse;
import com.homepedia.common.stats.DepartmentStats;
import com.homepedia.common.stats.RegionStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Stats", description = "Aggregate statistics across geographic levels")
@RestController
@RequestMapping(STATS)
@RequiredArgsConstructor
public class StatsController {

	private final StatsService statsService;

	@Operation(summary = "Aggregate stats per region (population, area, transactions, prices)")
	@GetMapping(HomepediaConstant.RestPath.Stats.REGIONS)
	public ResponseEntity<List<RegionStats>> regionStats() {
		return ResponseEntity.ok(statsService.regionStats());
	}

	@Operation(summary = "Aggregate stats per department (filterable by region)")
	@GetMapping(HomepediaConstant.RestPath.Stats.DEPARTMENTS)
	public ResponseEntity<List<DepartmentStats>> departmentStats(
			@Parameter(description = "Optional region code to filter by") @RequestParam(required = false) final String regionCode) {
		return ResponseEntity.ok(statsService.departmentStats(regionCode));
	}

	@Operation(summary = "Aggregate stats for a batch of communes (driven by viewport at city zoom)")
	@GetMapping(HomepediaConstant.RestPath.Stats.CITIES)
	public ResponseEntity<List<CityStats>> cityStats(
			@Parameter(description = "INSEE codes of the communes to aggregate (capped server-side)") @RequestParam final List<String> codes) {
		return ResponseEntity.ok(statsService.cityStats(codes));
	}

	@Operation(summary = "Pre-computed department stats from Spark (includes median price)")
	@GetMapping(HomepediaConstant.RestPath.Stats.DEPARTMENTS + "/precomputed")
	public ResponseEntity<List<DepartmentDvfStatsResponse>> precomputedDepartmentStats() {
		return ResponseEntity.ok(statsService.precomputedDepartmentStats());
	}

	@Operation(summary = "Pre-computed stats for a single department from Spark (includes median price)")
	@GetMapping(HomepediaConstant.RestPath.Stats.DEPARTMENTS + "/precomputed/{departmentCode}")
	public ResponseEntity<DepartmentDvfStatsResponse> precomputedDepartmentStatsByCode(
			@PathVariable final String departmentCode) {
		return statsService.precomputedDepartmentStats(departmentCode).map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}
}
