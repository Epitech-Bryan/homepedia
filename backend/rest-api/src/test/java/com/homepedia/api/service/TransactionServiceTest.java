package com.homepedia.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.homepedia.common.stats.StatsRepository;
import com.homepedia.common.stats.StatsRepository.TransactionStatsProjection;
import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Sister of {@link com.homepedia.api.regression.TransactionStatsRegressionTest}
 * — same business contract, smaller spot checks. Issue #3 moved the aggregation
 * from {@code transactionRepository.findAll(spec)} to the
 * {@code StatsRepository.aggregateTransactionStats} DB-side projection, so
 * mocks now feed a synthetic projection instead of a transaction list. Every
 * assertion below is preserved verbatim — only the mock plumbing changes.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

	@SuppressWarnings("unused")
	@Mock
	private TransactionRepository transactionRepository;

	@Mock
	private StatsRepository statsRepository;

	@InjectMocks
	private TransactionService transactionService;

	@Test
	void computeStats_withTransactions_returnsCorrectAggregates() {
		final var t1 = RealEstateTransaction.builder().propertyValue(new BigDecimal("100000")).builtSurface(50.0)
				.build();
		final var t2 = RealEstateTransaction.builder().propertyValue(new BigDecimal("200000")).builtSurface(100.0)
				.build();
		final var t3 = RealEstateTransaction.builder().propertyValue(new BigDecimal("300000")).builtSurface(75.0)
				.build();

		when(statsRepository.aggregateTransactionStats(any(), any(), any()))
				.thenReturn(projectionFor(List.of(t1, t2, t3)));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isEqualTo(3);
		assertThat(stats.averagePrice()).isEqualByComparingTo(new BigDecimal("200000.00"));
		assertThat(stats.medianPrice()).isEqualByComparingTo(new BigDecimal("200000"));
		assertThat(stats.minPrice()).isEqualByComparingTo(new BigDecimal("100000"));
		assertThat(stats.maxPrice()).isEqualByComparingTo(new BigDecimal("300000"));
		assertThat(stats.averageSurface()).isEqualTo(75.0);
		assertThat(stats.averagePricePerSqm()).isGreaterThan(0.0);
	}

	@Test
	void computeStats_emptyTransactions_returnsEmptyStats() {
		when(statsRepository.aggregateTransactionStats(any(), any(), any()))
				.thenReturn(projectionFor(Collections.emptyList()));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isZero();
		assertThat(stats.averagePrice()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(stats.medianPrice()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void computeStats_transactionsWithNullAndZeroPrices_ignoresInvalidPrices() {
		final var validTransaction = RealEstateTransaction.builder().propertyValue(new BigDecimal("150000"))
				.builtSurface(60.0).build();
		final var nullPriceTransaction = RealEstateTransaction.builder().propertyValue(null).builtSurface(80.0).build();
		final var zeroPriceTransaction = RealEstateTransaction.builder().propertyValue(BigDecimal.ZERO)
				.builtSurface(40.0).build();

		when(statsRepository.aggregateTransactionStats(any(), any(), any()))
				.thenReturn(projectionFor(List.of(validTransaction, nullPriceTransaction, zeroPriceTransaction)));

		final var stats = transactionService.computeStats(null, "75", null);

		assertThat(stats.totalTransactions()).isEqualTo(3);
		assertThat(stats.averagePrice()).isEqualByComparingTo(new BigDecimal("150000.00"));
		assertThat(stats.minPrice()).isEqualByComparingTo(new BigDecimal("150000"));
		assertThat(stats.maxPrice()).isEqualByComparingTo(new BigDecimal("150000"));
	}

	@Test
	void computeStats_allInvalidPrices_returnsEmptyStats() {
		final var t1 = RealEstateTransaction.builder().propertyValue(null).builtSurface(50.0).build();
		final var t2 = RealEstateTransaction.builder().propertyValue(BigDecimal.ZERO).builtSurface(60.0).build();

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(List.of(t1, t2)));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isZero();
		assertThat(stats.averagePrice()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void computeStats_transactionsWithNullSurface_handlesGracefully() {
		final var t1 = RealEstateTransaction.builder().propertyValue(new BigDecimal("200000")).builtSurface(null)
				.build();

		when(statsRepository.aggregateTransactionStats(any(), any(), any())).thenReturn(projectionFor(List.of(t1)));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isEqualTo(1);
		assertThat(stats.averageSurface()).isEqualTo(0.0);
		assertThat(stats.averagePricePerSqm()).isEqualTo(0.0);
	}

	/**
	 * Mirrors the DB-side aggregate the new {@code aggregateTransactionStats} query
	 * returns — same upper-middle-for-even median, same valid-only filter for
	 * averages, same null-when-empty semantics. Keeps the test cases input-output
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
