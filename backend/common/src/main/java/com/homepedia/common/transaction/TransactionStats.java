package com.homepedia.common.transaction;

import java.math.BigDecimal;

public record TransactionStats(long totalTransactions, BigDecimal averagePrice, BigDecimal medianPrice,
		BigDecimal minPrice, BigDecimal maxPrice, Double averageSurface, Double averagePricePerSquareMeter) {
}
