package com.homepedia.common.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "city_reviews", indexes = {@Index(name = "idx_review_city", columnList = "city_insee_code")})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
public class CityReview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 5)
	private final String cityInseeCode;

	@Column(columnDefinition = "TEXT")
	private String content;

	private Double sentimentScore;

	private String sentimentLabel;

	private LocalDate publishedAt;

	private String author;

	private Double rating;
}
