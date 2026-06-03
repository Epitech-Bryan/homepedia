package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.TRANSACTIONS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Transaction.BY_ID;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Transaction.COMPARABLE_SALES;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Transaction.HEATPOINTS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Transaction.MARKERS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Transaction.STATS;

import com.homepedia.api.service.ComparableSalesService;
import com.homepedia.api.service.TransactionHeatPointService;
import com.homepedia.api.service.TransactionMarkerService;
import com.homepedia.api.service.TransactionService;
import com.homepedia.common.transaction.ComparableSale;
import com.homepedia.common.transaction.PropertyType;
import com.homepedia.common.transaction.TransactionDetail;
import com.homepedia.common.transaction.TransactionHeatPoint;
import com.homepedia.common.transaction.TransactionMarker;
import com.homepedia.common.transaction.TransactionStats;
import com.homepedia.common.transaction.TransactionSummary;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
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

@Tag(name = "Transactions", description = "Real estate transactions (DVF)")
@RestController
@RequestMapping(TRANSACTIONS)
@RequiredArgsConstructor
public class TransactionController {

	private final TransactionService transactionService;
	private final TransactionHeatPointService heatPointService;
	private final TransactionMarkerService markerService;
	private final ComparableSalesService comparableSalesService;
	private final PagedResourcesAssembler<TransactionSummary> pagedResourcesAssembler;

	@Operation(summary = "Search transactions", description = "Paginated real estate transactions with multi-criteria filtering")
	@GetMapping
	public ResponseEntity<PagedModel<EntityModel<TransactionSummary>>> search(
			@Parameter(description = "City INSEE code") @RequestParam(required = false) final String cityInseeCode,
			@Parameter(description = "Department code") @RequestParam(required = false) final String departmentCode,
			@Parameter(description = "Transaction year") @RequestParam(required = false) final Integer year,
			@Parameter(description = "Minimum price") @RequestParam(required = false) final BigDecimal minPrice,
			@Parameter(description = "Maximum price") @RequestParam(required = false) final BigDecimal maxPrice,
			@Parameter(description = "Property type") @RequestParam(required = false) final PropertyType propertyType,
			final Pageable pageable) {
		final var page = transactionService.search(cityInseeCode, departmentCode, year, minPrice, maxPrice,
				propertyType, pageable);
		return ResponseEntity.ok(pagedResourcesAssembler.toModel(page));
	}

	@Operation(summary = "Transaction detail", description = "Full detail for a single transaction")
	@GetMapping(BY_ID)
	public ResponseEntity<TransactionDetail> getById(@PathVariable final Long id) {
		return transactionService.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@Operation(summary = "Transaction statistics", description = "Aggregated price statistics for the given filters")
	@GetMapping(STATS)
	public ResponseEntity<TransactionStats> stats(
			@Parameter(description = "City INSEE code") @RequestParam(required = false) final String cityInseeCode,
			@Parameter(description = "Department code") @RequestParam(required = false) final String departmentCode,
			@Parameter(description = "Transaction year") @RequestParam(required = false) final Integer year) {
		return ResponseEntity.ok(transactionService.computeStats(cityInseeCode, departmentCode, year));
	}

	@Operation(summary = "Heatmap points by viewport", description = "Aggregates geocoded transactions inside the viewport into ~100 m grid buckets so the frontend can render a precise heatmap. Empty when the viewport is wider than 5°.")
	@GetMapping(HEATPOINTS)
	public ResponseEntity<List<TransactionHeatPoint>> heatPoints(
			@Parameter(description = "Viewport south latitude") @RequestParam final double south,
			@Parameter(description = "Viewport west longitude") @RequestParam final double west,
			@Parameter(description = "Viewport north latitude") @RequestParam final double north,
			@Parameter(description = "Viewport east longitude") @RequestParam final double east,
			@Parameter(description = "Metric to aggregate (averagePrice, averagePricePerSqm, transactionCount)") @RequestParam(defaultValue = "averagePricePerSqm") final String metric) {
		return ResponseEntity.ok(heatPointService.heatPoints(south, west, north, east, parseMetric(metric)));
	}

	@Operation(summary = "Heatmap points as a packed Float32 buffer", description = "Same aggregation as /heatpoints but encoded as little-endian Float32 triples (lat, lon, value) — ~4x smaller than the JSON form and no parse cost, used by the map heat layer.")
	@GetMapping(value = HEATPOINTS
			+ "/binary", produces = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<byte[]> heatPointsBinary(
			@Parameter(description = "Viewport south latitude") @RequestParam final double south,
			@Parameter(description = "Viewport west longitude") @RequestParam final double west,
			@Parameter(description = "Viewport north latitude") @RequestParam final double north,
			@Parameter(description = "Viewport east longitude") @RequestParam final double east,
			@Parameter(description = "Metric to aggregate") @RequestParam(defaultValue = "averagePricePerSqm") final String metric) {
		final var points = heatPointService.heatPoints(south, west, north, east, parseMetric(metric));
		// Little-endian so a JS Float32Array reads it directly (native byte order
		// on every platform we ship to). Three floats per point: lat, lon, value.
		final var buffer = java.nio.ByteBuffer.allocate(points.size() * 3 * Float.BYTES)
				.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		for (final var p : points) {
			buffer.putFloat((float) p.latitude());
			buffer.putFloat((float) p.longitude());
			buffer.putFloat((float) p.value());
		}
		return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
				.body(buffer.array());
	}

	private static TransactionHeatPointService.Metric parseMetric(final String metric) {
		return switch (metric) {
			case "averagePrice" -> TransactionHeatPointService.Metric.AVERAGE_PRICE;
			case "transactionCount" -> TransactionHeatPointService.Metric.TRANSACTION_COUNT;
			default -> TransactionHeatPointService.Metric.AVERAGE_PRICE_PER_SQM;
		};
	}

	@Operation(summary = "Transaction markers by viewport", description = "Returns individual geocoded transactions inside the viewport (id, lat/lon, price, surface, …) so the map can render clickable pins. Empty when the viewport is wider than 0.2°.")
	@GetMapping(MARKERS)
	public ResponseEntity<List<TransactionMarker>> markers(
			@Parameter(description = "Viewport south latitude") @RequestParam final double south,
			@Parameter(description = "Viewport west longitude") @RequestParam final double west,
			@Parameter(description = "Viewport north latitude") @RequestParam final double north,
			@Parameter(description = "Viewport east longitude") @RequestParam final double east,
			@Parameter(description = "Property type filter") @RequestParam(required = false) final PropertyType propertyType,
			@Parameter(description = "Minimum price") @RequestParam(required = false) final BigDecimal minPrice,
			@Parameter(description = "Maximum price") @RequestParam(required = false) final BigDecimal maxPrice,
			@Parameter(description = "Cap on returned markers (default 300, max 1000)") @RequestParam(required = false) final Integer limit) {
		return ResponseEntity
				.ok(markerService.markers(south, west, north, east, propertyType, minPrice, maxPrice, limit));
	}

	@Operation(summary = "Nearest comparable sales", description = "Top-N pre-computed comparable transactions for the requested mutation, ordered by similarity rank. Returns an empty array until the ComparableSalesAggregator Spark job populates the table; the endpoint is live now so the frontend popup can be developed against the final contract.")
	@GetMapping(COMPARABLE_SALES)
	public ResponseEntity<List<ComparableSale>> comparableSales(@PathVariable final Long id) {
		return ResponseEntity.ok(comparableSalesService.findByTransactionId(id));
	}
}
