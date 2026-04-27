package com.homepedia.api.controller;

import com.homepedia.api.service.CacheInvalidationService;
import com.homepedia.api.service.SparkJobLauncherService;
import com.homepedia.api.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Admin", description = "Administrative operations")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

	private final CacheInvalidationService cacheInvalidationService;
	private final StatsService statsService;
	private final SparkJobLauncherService sparkJobLauncherService;

	@Operation(summary = "Recompute all statistics", description = "Evicts stats caches, optionally triggers Spark DVF aggregation, and warms up caches.")
	@PostMapping("/recompute-stats")
	public ResponseEntity<RecomputeResponse> recomputeStats(@RequestParam(defaultValue = "true") final boolean warmup,
			@RequestParam(defaultValue = "true") final boolean spark) {
		log.info("Manual stats recomputation triggered (warmup={}, spark={})", warmup, spark);

		cacheInvalidationService.evictStats();

		final var sparkTriggered = spark && sparkJobLauncherService.isEnabled();
		if (sparkTriggered) {
			sparkJobLauncherService.launchDvfAggregateJob();
		}

		var regionsWarmed = 0;
		var departmentsWarmed = 0;
		if (warmup) {
			regionsWarmed = statsService.regionStats().size();
			departmentsWarmed = statsService.departmentStats(null).size();
			log.info("Stats warm-up complete: {} regions, {} departments", regionsWarmed, departmentsWarmed);
		}

		final var message = buildMessage(warmup, sparkTriggered);
		return ResponseEntity
				.ok(new RecomputeResponse(message, regionsWarmed, departmentsWarmed, sparkTriggered, Instant.now()));
	}

	@Operation(summary = "Evict all caches", description = "Evicts all application caches (refdata, geo, stats, reviews)")
	@PostMapping("/evict-all-caches")
	public ResponseEntity<EvictResponse> evictAllCaches() {
		log.info("Manual full cache eviction triggered");
		cacheInvalidationService.evictAll();
		return ResponseEntity.ok(new EvictResponse("All caches evicted", Instant.now()));
	}

	private static String buildMessage(boolean warmup, boolean sparkTriggered) {
		final var sb = new StringBuilder("Stats cache evicted");
		if (warmup) {
			sb.append(", caches warmed up");
		}
		if (sparkTriggered) {
			sb.append(", Spark DVF aggregation launched (async — follow progress via SSE)");
		}
		return sb.toString();
	}

	public record RecomputeResponse(String message, int regionsWarmed, int departmentsWarmed, boolean sparkTriggered,
			Instant at) {
	}

	public record EvictResponse(String message, Instant at) {
	}
}
