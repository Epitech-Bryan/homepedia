package com.homepedia.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real-User Monitoring sink. The webapp ships every Core Web Vital (LCP, INP,
 * CLS, TTFB, FCP) here via {@code navigator.sendBeacon} so we see what real
 * users actually experience instead of synthetic Lighthouse numbers.
 *
 * <p>
 * For now we just log the metric — Loki / Promtail picks it up via the standard
 * pod stdout. Wire to a Prometheus pushgateway or a dedicated metrics endpoint
 * once we have the infra. Returns 204 No Content so the beacon is
 * fire-and-forget on the client side.
 */
@Slf4j
@Tag(name = "RUM", description = "Real-User Monitoring beacon endpoint")
@RestController
@RequestMapping("/rum")
public class RumController {

	@PostMapping
	public ResponseEntity<Void> ingest(@Valid @RequestBody RumMetric metric) {
		log.info("RUM {} {} value={} rating={} path={}", metric.name(), metric.id(), metric.value(), metric.rating(),
				metric.path());
		return ResponseEntity.noContent().build();
	}

	public record RumMetric(@NotBlank String name, @NotNull Double value, String rating, Double delta,
			@NotBlank String id, String navigationType, String path, Long ts) {
	}
}
