package com.homepedia.common.review;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

/**
 * City review stored in MongoDB. Reviews are document-shaped (free text +
 * sentiment metadata) and are queried mostly by city, so a non-relational store
 * fits naturally and complements the relational data in PostgreSQL.
 */
@Document(collection = "city_reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
public class CityReview {

	@Id
	private String id;

	@Indexed
	private String cityInseeCode;

	// Mongo text index enables $text full-text search over review bodies
	// (powered by the language-aware tokeniser, French stemming, stopwords).
	// Not used yet — the word-cloud / sentiment endpoints stream raw docs —
	// but it's cheap to keep in place so a "search reviews mentioning X"
	// endpoint can land without a migration.
	@TextIndexed
	private String content;

	private Double sentimentScore;

	private String sentimentLabel;

	private LocalDate publishedAt;

	private String author;

	private Double rating;
}
