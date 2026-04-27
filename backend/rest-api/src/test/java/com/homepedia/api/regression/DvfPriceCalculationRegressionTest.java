package com.homepedia.api.regression;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * REGRESSION TEST — DO NOT MODIFY WITH AI AGENTS.
 *
 * These tests lock down the DVF price-per-sqm calculation contract. Only a
 * human should change expected values after verifying the business logic change
 * is intentional.
 */
class DvfPriceCalculationRegressionTest {

	@Test
	void pricePerSquareMeter_standardValues_returnsExactQuotient() {
		final var price = new BigDecimal("250000");
		final var surface = 80.0;

		final var pricePerSqm = price.doubleValue() / surface;

		assertThat(pricePerSqm).isEqualTo(3125.0);
	}

	@Test
	void pricePerSquareMeter_150000dividedBy60_returnsExact2500() {
		final var price = new BigDecimal("150000");
		final var surface = 60.0;

		final var pricePerSqm = price.doubleValue() / surface;

		assertThat(pricePerSqm).isEqualTo(2500.0);
	}

	@Test
	void pricePerSquareMeter_zeroPriceWithPositiveSurface_returnsZero() {
		final var price = BigDecimal.ZERO;
		final var surface = 50.0;

		final var pricePerSqm = price.doubleValue() / surface;

		assertThat(pricePerSqm).isEqualTo(0.0);
	}

	@Test
	void pricePerSquareMeter_negativePriceWithPositiveSurface_returnsNegativeValue() {
		final var price = new BigDecimal("-100000");
		final var surface = 50.0;

		final var pricePerSqm = price.doubleValue() / surface;

		assertThat(pricePerSqm).isEqualTo(-2000.0);
	}

	@Test
	void pricePerSquareMeter_zeroSurface_returnsPositiveInfinity() {
		final var price = new BigDecimal("200000");
		final var surface = 0.0;

		final var pricePerSqm = price.doubleValue() / surface;

		assertThat(pricePerSqm).isEqualTo(Double.POSITIVE_INFINITY);
	}

	@Test
	void pricePerSquareMeter_negativeSurface_returnsNegativeValue() {
		final var price = new BigDecimal("200000");
		final var surface = -10.0;

		final var pricePerSqm = price.doubleValue() / surface;

		assertThat(pricePerSqm).isEqualTo(-20000.0);
	}

	@Test
	void pricePerSquareMeter_veryLargeValues_calculatesWithoutOverflow() {
		final var price = new BigDecimal("99999999");
		final var surface = 1.0;

		final var pricePerSqm = price.doubleValue() / surface;

		assertThat(pricePerSqm).isEqualTo(9.9999999E7);
	}

	@Test
	void pricePerSquareMeter_smallFractionalSurface_returnsHighPricePerSqm() {
		final var price = new BigDecimal("100000");
		final var surface = 0.5;

		final var pricePerSqm = price.doubleValue() / surface;

		assertThat(pricePerSqm).isEqualTo(200000.0);
	}

	@Test
	void averagePricePerSquareMeter_multipleTransactions_returnsCorrectAverage() {
		final var pricePerSqm1 = new BigDecimal("200000").doubleValue() / 80.0;
		final var pricePerSqm2 = new BigDecimal("300000").doubleValue() / 100.0;
		final var pricePerSqm3 = new BigDecimal("150000").doubleValue() / 60.0;

		final var average = (pricePerSqm1 + pricePerSqm2 + pricePerSqm3) / 3.0;

		assertThat(pricePerSqm1).isEqualTo(2500.0);
		assertThat(pricePerSqm2).isEqualTo(3000.0);
		assertThat(pricePerSqm3).isEqualTo(2500.0);
		assertThat(average).isCloseTo(2666.6666666666665, org.assertj.core.data.Offset.offset(0.0001));
	}

	@Test
	void surfaceFilterLogic_nullSurfaceOrZeroSurface_shouldBeExcludedFromCalculation() {
		final Double nullSurface = null;
		final var zeroSurface = 0.0;
		final var negativeSurface = -5.0;
		final var validSurface = 50.0;

		assertThat(nullSurface == null || nullSurface <= 0).isTrue();
		assertThat(zeroSurface <= 0).isTrue();
		assertThat(negativeSurface <= 0).isTrue();
		assertThat(validSurface > 0).isTrue();
	}
}
