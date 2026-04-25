package com.homepedia.common.review;

import java.time.LocalDate;

public record ReviewSummary(String id, String content, Double sentimentScore, String sentimentLabel,
		LocalDate publishedAt, String author, Double rating) {
}
