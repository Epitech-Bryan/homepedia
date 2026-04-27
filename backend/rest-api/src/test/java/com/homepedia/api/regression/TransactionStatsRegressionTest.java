package com.homepedia.api.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.homepedia.api.service.TransactionService;
import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

/**
 * REGRESSION TEST — DO NOT MODIFY WITH AI AGENTS.
 *
 * Contract tests for transaction stats aggregation. Expected values are locked
 * to known inputs and must not change unless the business logic intentionally
 * changes.
 */
@ExtendWith(MockitoExtension.class)
class TransactionStatsRegressionTest {

	@Mock
	private TransactionRepository transactionRepository;

	@InjectMocks
	private TransactionService transactionService;

	@Test
	void computeStats_fiveKnownTransactions_averageIsExact220000() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("150000"), 55.0), transaction(new BigDecimal("200000"), 70.0),
				transaction(new BigDecimal("250000"), 90.0), transaction(new BigDecimal("400000"), 120.0));

		when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isEqualTo(5);
		assertThat(stats.averagePrice()).isEqualByComparingTo(new BigDecimal("220000.00"));
	}

	@Test
	void computeStats_fiveKnownTransactions_medianIsMiddleElement() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("150000"), 55.0), transaction(new BigDecimal("200000"), 70.0),
				transaction(new BigDecimal("250000"), 90.0), transaction(new BigDecimal("400000"), 120.0));

		when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.medianPrice()).isEqualByComparingTo(new BigDecimal("200000"));
	}

	@Test
	void computeStats_fiveKnownTransactions_minAndMaxAreExact() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("150000"), 55.0), transaction(new BigDecimal("200000"), 70.0),
				transaction(new BigDecimal("250000"), 90.0), transaction(new BigDecimal("400000"), 120.0));

		when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.minPrice()).isEqualByComparingTo(new BigDecimal("100000"));
		assertThat(stats.maxPrice()).isEqualByComparingTo(new BigDecimal("400000"));
	}

	@Test
	void computeStats_fiveKnownTransactions_averageSurfaceIsExact75() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("150000"), 55.0), transaction(new BigDecimal("200000"), 70.0),
				transaction(new BigDecimal("250000"), 90.0), transaction(new BigDecimal("400000"), 120.0));

		when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.averageSurface()).isEqualTo(75.0);
	}

	@Test
	void computeStats_fiveKnownTransactions_averagePricePerSqmIsExact() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("150000"), 55.0), transaction(new BigDecimal("200000"), 70.0),
				transaction(new BigDecimal("250000"), 90.0), transaction(new BigDecimal("400000"), 120.0));

		when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);

		final var stats = transactionService.computeStats("75056", null, null);

		final var expectedPerSqm = (100000.0 / 40.0 + 150000.0 / 55.0 + 200000.0 / 70.0 + 250000.0 / 90.0
				+ 400000.0 / 120.0) / 5.0;
		assertThat(stats.averagePricePerSqm()).isCloseTo(expectedPerSqm, Offset.offset(0.01));
	}

	@Test
	void computeStats_emptyTransactionList_returnsZeroedStats() {
		when(transactionRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isZero();
		assertThat(stats.averagePrice()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(stats.medianPrice()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(stats.minPrice()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(stats.maxPrice()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(stats.averageSurface()).isEqualTo(0.0);
		assertThat(stats.averagePricePerSqm()).isEqualTo(0.0);
	}

	@Test
	void computeStats_singleTransaction_allStatsEqualThatTransaction() {
		final var transactions = List.of(transaction(new BigDecimal("175000"), 65.0));

		when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isEqualTo(1);
		assertThat(stats.averagePrice()).isEqualByComparingTo(new BigDecimal("175000.00"));
		assertThat(stats.medianPrice()).isEqualByComparingTo(new BigDecimal("175000"));
		assertThat(stats.minPrice()).isEqualByComparingTo(new BigDecimal("175000"));
		assertThat(stats.maxPrice()).isEqualByComparingTo(new BigDecimal("175000"));
		assertThat(stats.averageSurface()).isEqualTo(65.0);
		assertThat(stats.averagePricePerSqm()).isCloseTo(175000.0 / 65.0, Offset.offset(0.01));
	}

	@Test
	void computeStats_evenNumberOfTransactions_medianIsUpperMiddleElement() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("200000"), 60.0), transaction(new BigDecimal("300000"), 80.0),
				transaction(new BigDecimal("400000"), 100.0));

		when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.medianPrice()).isEqualByComparingTo(new BigDecimal("300000"));
	}

	@Test
	void computeStats_mixedValidAndInvalidPrices_onlyValidPricesContributeToStats() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 50.0), transaction(null, 60.0),
				transaction(BigDecimal.ZERO, 40.0), transaction(new BigDecimal("300000"), 80.0));

		when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isEqualTo(4);
		assertThat(stats.averagePrice()).isEqualByComparingTo(new BigDecimal("200000.00"));
		assertThat(stats.medianPrice()).isEqualByComparingTo(new BigDecimal("300000"));
		assertThat(stats.minPrice()).isEqualByComparingTo(new BigDecimal("100000"));
		assertThat(stats.maxPrice()).isEqualByComparingTo(new BigDecimal("300000"));
	}

	@Test
	void computeStats_nullAndZeroSurfaces_excludedFromSurfaceAverageAndPricePerSqm() {
		final var transactions = List.of(transaction(new BigDecimal("200000"), 80.0),
				transaction(new BigDecimal("100000"), null), transaction(new BigDecimal("150000"), 0.0));

		when(transactionRepository.findAll(any(Specification.class))).thenReturn(transactions);

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.averageSurface()).isEqualTo(80.0);
		assertThat(stats.averagePricePerSqm()).isCloseTo(200000.0 / 80.0, Offset.offset(0.01));
	}

	private static RealEstateTransaction transaction(BigDecimal price, Double surface) {
		return RealEstateTransaction.builder().propertyValue(price).builtSurface(surface).build();
	}
}
