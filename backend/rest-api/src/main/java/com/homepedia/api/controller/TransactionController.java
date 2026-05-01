package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.TRANSACTIONS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Transaction.BY_ID;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Transaction.HEATPOINTS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Transaction.STATS;

import com.homepedia.api.service.TransactionHeatPointService;
import com.homepedia.api.service.TransactionService;
import com.homepedia.common.transaction.PropertyType;
import com.homepedia.common.transaction.TransactionDetail;
import com.homepedia.common.transaction.TransactionHeatPoint;
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
		final TransactionHeatPointService.Metric m = switch (metric) {
			case "averagePrice" -> TransactionHeatPointService.Metric.AVERAGE_PRICE;
			case "transactionCount" -> TransactionHeatPointService.Metric.TRANSACTION_COUNT;
			default -> TransactionHeatPointService.Metric.AVERAGE_PRICE_PER_SQM;
		};
		return ResponseEntity.ok(heatPointService.heatPoints(south, west, north, east, m));
	}
}
