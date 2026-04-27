package com.homepedia.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

	@Mock
	private TransactionRepository transactionRepository;

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

		when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(List.of(t1, t2, t3));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isEqualTo(3);
		assertThat(stats.averagePrice()).isEqualByComparingTo(new BigDecimal("200000.00"));
		assertThat(stats.medianPrice()).isEqualByComparingTo(new BigDecimal("200000"));
		assertThat(stats.minPrice()).isEqualByComparingTo(new BigDecimal("100000"));
		assertThat(stats.maxPrice()).isEqualByComparingTo(new BigDecimal("300000"));
		assertThat(stats.averageSurface()).isEqualTo(75.0);
		assertThat(stats.averagePricePerSquareMeter()).isGreaterThan(0.0);
	}

	@Test
	void computeStats_emptyTransactions_returnsEmptyStats() {
		when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(Collections.emptyList());

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

		when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(List.of(validTransaction, nullPriceTransaction, zeroPriceTransaction));

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

		when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(List.of(t1, t2));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isZero();
		assertThat(stats.averagePrice()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void computeStats_transactionsWithNullSurface_handlesGracefully() {
		final var t1 = RealEstateTransaction.builder().propertyValue(new BigDecimal("200000")).builtSurface(null)
				.build();

		when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(List.of(t1));

		final var stats = transactionService.computeStats("75056", null, null);

		assertThat(stats.totalTransactions()).isEqualTo(1);
		assertThat(stats.averageSurface()).isEqualTo(0.0);
		assertThat(stats.averagePricePerSquareMeter()).isEqualTo(0.0);
	}
}
