package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.TRANSACTIONS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Transaction.STATS;

import com.homepedia.api.service.TransactionService;
import com.homepedia.common.transaction.PropertyType;
import com.homepedia.common.transaction.TransactionStats;
import com.homepedia.common.transaction.TransactionSummary;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transactions", description = "Real estate transactions (DVF)")
@RestController
@RequestMapping(TRANSACTIONS)
@RequiredArgsConstructor
public class TransactionController {

	private final TransactionService transactionService;
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

	@Operation(summary = "Transaction statistics", description = "Aggregated price statistics for the given filters")
	@GetMapping(STATS)
	public ResponseEntity<TransactionStats> stats(
			@Parameter(description = "City INSEE code") @RequestParam(required = false) final String cityInseeCode,
			@Parameter(description = "Department code") @RequestParam(required = false) final String departmentCode,
			@Parameter(description = "Transaction year") @RequestParam(required = false) final Integer year) {
		return ResponseEntity.ok(transactionService.computeStats(cityInseeCode, departmentCode, year));
	}
}
