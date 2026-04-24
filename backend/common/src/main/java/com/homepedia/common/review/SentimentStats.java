package com.homepedia.common.review;

public record SentimentStats(double averageScore, long positiveCount, long negativeCount, long neutralCount,
		long totalReviews) {
}
