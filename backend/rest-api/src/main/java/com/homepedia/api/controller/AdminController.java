package com.homepedia.api.controller;

import com.homepedia.api.admin.AdminJobsService;
import com.homepedia.api.service.CacheInvalidationService;
import com.homepedia.api.service.SparkJobLauncherService;
import com.homepedia.api.service.StatsService;
import com.homepedia.api.service.TransactionsPartitionStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Admin", description = "Administrative operations (auth required)")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

	private final CacheInvalidationService cacheInvalidationService;
	private final StatsService statsService;
	private final SparkJobLauncherService sparkJobLauncherService;
	private final AdminJobsService adminJobsService;
	private final TransactionsPartitionStatsService transactionsPartitionStatsService;

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

	@Operation(summary = "List Redis caches", description = "Returns the names of all caches that can be evicted from the admin UI.")
	@GetMapping("/caches")
	public ResponseEntity<List<String>> listCaches() {
		return ResponseEntity.ok(CacheInvalidationService.AVAILABLE_CACHES);
	}

	@Operation(summary = "Evict a single cache", description = "Clears the named Redis cache. Returns 404 if the cache name is not registered.")
	@PostMapping("/caches/{name}/evict")
	public ResponseEntity<EvictResponse> evictCache(@PathVariable String name) {
		final var cleared = cacheInvalidationService.evictByName(name);
		if (!cleared) {
			return ResponseEntity.notFound().build();
		}
		log.info("Manual cache eviction triggered: {}", name);
		return ResponseEntity.ok(new EvictResponse("Cache '" + name + "' evicted", Instant.now()));
	}

	@Operation(summary = "Per-year transaction counts", description = "Returns the number of rows in each yearly partition of the `transactions` table. Useful to see DVF import progress over time.")
	@GetMapping("/transactions/partition-stats")
	public ResponseEntity<List<TransactionsPartitionStatsService.YearCount>> partitionStats() {
		return ResponseEntity.ok(transactionsPartitionStatsService.countByYear());
	}

	@Operation(summary = "Truncate a yearly transactions partition", description = "Empties `transactions_<year>` (DVF data for that year). Refuses with 409 if a DVF import is currently running. Use to reset before a re-import.")
	@DeleteMapping("/transactions/{year}")
	public ResponseEntity<TruncateResponse> truncateYear(@PathVariable int year) {
		log.warn("Manual partition truncation triggered for year {}", year);
		transactionsPartitionStatsService.truncateYear(year);
		return ResponseEntity.ok(new TruncateResponse(year, "truncated", Instant.now()));
	}

	@Operation(summary = "List import jobs status", description = "Returns the current state of every Spring Batch import job (RUNNING/IDLE + last run metadata).")
	@GetMapping("/jobs/status")
	public ResponseEntity<Map<String, AdminJobsService.JobStatusView>> jobsStatus() {
		return ResponseEntity.ok(adminJobsService.statusAll());
	}

	@Operation(summary = "Trigger an import job", description = "Async — launches the named job (slug, e.g. 'dvf'). Any additional query parameters are forwarded as Spring Batch job parameters (e.g. ?year=2024). Returns 202 if dispatched, 409 if already running, 404 if unknown.")
	@PostMapping("/imports/{slug}")
	public ResponseEntity<TriggerResponse> triggerImport(@PathVariable String slug,
			@RequestParam Map<String, String> queryParams) {
		// Strip the path variable from the query map (Spring includes it on some
		// configurations) so it doesn't accidentally land in JobParameters.
		final var extras = queryParams.entrySet().stream().filter(e -> !"slug".equals(e.getKey()))
				.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		adminJobsService.trigger(slug, extras);
		return ResponseEntity.accepted().body(new TriggerResponse(slug, "dispatched", Instant.now()));
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

	public record TriggerResponse(String job, String state, Instant at) {
	}

	public record TruncateResponse(int year, String state, Instant at) {
	}
}
