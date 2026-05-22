package com.homepedia.api.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.homepedia.api.service.TransactionService;
import com.homepedia.common.stats.StatsRepository;
import com.homepedia.common.stats.StatsRepository.TransactionStatsProjection;
import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * REGRESSION TEST — DO NOT MODIFY WITH AI AGENTS.
 *
 * Contract tests for transaction stats aggregation. Expected values are locked
 * to known inputs and must not change unless the business logic intentionally
 * changes.
 *
 * <p>
 * Issue #3 ported the aggregation from a JVM-side stream of {@code
 * transactionRepository.findAll(spec)} to a single DB-side aggregate via
 * {@code StatsRepository.aggregateTransactionStats}. The mocks below now feed a
 * {@code TransactionStatsProjection} computed from the same known inputs —
 * every assertion value is preserved verbatim.
 */
@ExtendWith(MockitoExtension.class)
class TransactionStatsRegressionTest {

	@SuppressWarnings("unused")
	@Mock
	private TransactionRepository transactionRepository;

	@Mock
	private StatsRepository statsRepository;

	@InjectMocks
	private TransactionService transactionService;

	@Test
	void computeStats_fiveKnownTransactions_averageIsExact220000() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("150000"), 55.0), transaction(new BigDecimal("200000"), 70.0),
				transaction(new BigDecimal("250000"), 90.0), transaction(new BigDecimal("400000"), 120.0));

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(transactions));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isEqualTo(5);
		assertThat(stats.averagePrice()).isEqualByComparingTo(new BigDecimal("220000.00"));
	}

	@Test
	void computeStats_fiveKnownTransactions_medianIsMiddleElement() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("150000"), 55.0), transaction(new BigDecimal("200000"), 70.0),
				transaction(new BigDecimal("250000"), 90.0), transaction(new BigDecimal("400000"), 120.0));

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(transactions));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.medianPrice()).isEqualByComparingTo(new BigDecimal("200000"));
	}

	@Test
	void computeStats_fiveKnownTransactions_minAndMaxAreExact() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("150000"), 55.0), transaction(new BigDecimal("200000"), 70.0),
				transaction(new BigDecimal("250000"), 90.0), transaction(new BigDecimal("400000"), 120.0));

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(transactions));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.minPrice()).isEqualByComparingTo(new BigDecimal("100000"));
		assertThat(stats.maxPrice()).isEqualByComparingTo(new BigDecimal("400000"));
	}

	@Test
	void computeStats_fiveKnownTransactions_averageSurfaceIsExact75() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("150000"), 55.0), transaction(new BigDecimal("200000"), 70.0),
				transaction(new BigDecimal("250000"), 90.0), transaction(new BigDecimal("400000"), 120.0));

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(transactions));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.averageSurface()).isEqualTo(75.0);
	}

	@Test
	void computeStats_fiveKnownTransactions_averagePricePerSqmIsExact() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 40.0),
				transaction(new BigDecimal("150000"), 55.0), transaction(new BigDecimal("200000"), 70.0),
				transaction(new BigDecimal("250000"), 90.0), transaction(new BigDecimal("400000"), 120.0));

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(transactions));

		final var stats = transactionService.computeStats("75056", null, null);

		final var expectedPerSqm = (100000.0 / 40.0 + 150000.0 / 55.0 + 200000.0 / 70.0 + 250000.0 / 90.0
				+ 400000.0 / 120.0) / 5.0;
		assertThat(stats.averagePricePerSqm()).isCloseTo(expectedPerSqm, Offset.offset(0.01));
	}

	@Test
	void computeStats_emptyTransactionList_returnsZeroedStats() {
		when(statsRepository.aggregateTransactionStats(any(), any(), any()))
				.thenReturn(projectionFor(Collections.emptyList()));

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

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(transactions));

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

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(transactions));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.medianPrice()).isEqualByComparingTo(new BigDecimal("300000"));
	}

	@Test
	void computeStats_mixedValidAndInvalidPrices_onlyValidPricesContributeToStats() {
		final var transactions = List.of(transaction(new BigDecimal("100000"), 50.0), transaction(null, 60.0),
				transaction(BigDecimal.ZERO, 40.0), transaction(new BigDecimal("300000"), 80.0));

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(transactions));

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

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(transactions));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.averageSurface()).isEqualTo(80.0);
		assertThat(stats.averagePricePerSqm()).isCloseTo(200000.0 / 80.0, Offset.offset(0.01));
	}

	private static RealEstateTransaction transaction(BigDecimal price, Double surface) {
		return RealEstateTransaction.builder().propertyValue(price).builtSurface(surface).build();
	}

	/**
	 * Builds a {@link TransactionStatsProjection} mirroring the DB-side aggregate
	 * the new {@code aggregateTransactionStats} query returns — driven by the exact
	 * same input list the legacy mock used to feed
	 * {@code transactionRepository.findAll}. Keeps the test cases pure input-output
	 * and the assertion values locked.
	 */
	private static TransactionStatsProjection projectionFor(final List<RealEstateTransaction> transactions) {
		final long total = transactions.size();
		final var validPrices = transactions.stream().map(RealEstateTransaction::getPropertyValue)
				.filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0).sorted().toList();
		final BigDecimal avg;
		final BigDecimal min;
		final BigDecimal max;
		final BigDecimal median;
		if (validPrices.isEmpty()) {
			avg = null;
			min = null;
			max = null;
			median = null;
		} else {
			final var sum = validPrices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
			avg = sum.divide(BigDecimal.valueOf(validPrices.size()), 2, RoundingMode.HALF_UP);
			min = validPrices.getFirst();
			max = validPrices.getLast();
			median = validPrices.get(validPrices.size() / 2);
		}
		final Double avgSurface = transactions.stream().map(RealEstateTransaction::getBuiltSurface)
				.filter(s -> s != null && s > 0).mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
		final Double avgPricePerSqm = transactions.stream()
				.filter(t -> t.getPropertyValue() != null && t.getBuiltSurface() != null && t.getBuiltSurface() > 0
						&& t.getPropertyValue().compareTo(BigDecimal.ZERO) > 0)
				.mapToDouble(t -> t.getPropertyValue().doubleValue() / t.getBuiltSurface()).average()
				.orElse(Double.NaN);
		return new TransactionStatsProjection() {
			@Override
			public Long getTotalTransactions() {
				return total;
			}

			@Override
			public BigDecimal getAveragePrice() {
				return avg;
			}

			@Override
			public BigDecimal getMinPrice() {
				return min;
			}

			@Override
			public BigDecimal getMaxPrice() {
				return max;
			}

			@Override
			public BigDecimal getMedianPrice() {
				return median;
			}

			@Override
			public Double getAverageSurface() {
				return Double.isNaN(avgSurface) ? null : avgSurface;
			}

			@Override
			public Double getAveragePricePerSqm() {
				return Double.isNaN(avgPricePerSqm) ? null : avgPricePerSqm;
			}
		};
	}
}
