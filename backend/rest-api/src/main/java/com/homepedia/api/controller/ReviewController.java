package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.City.REVIEWS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.City.SENTIMENT_STATS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.City.WORD_CLOUD;

import com.homepedia.api.service.ReviewService;
import com.homepedia.common.review.ReviewSummary;
import com.homepedia.common.review.SentimentStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reviews", description = "City reviews and sentiment analysis")
@RestController
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;
	private final PagedResourcesAssembler<ReviewSummary> pagedResourcesAssembler;

	@Operation(summary = "List reviews for a city", description = "Paginated reviews with sentiment data for a given city")
	@GetMapping(REVIEWS)
	public PagedModel<EntityModel<ReviewSummary>> findByCityInseeCode(
			@Parameter(description = "City INSEE code") @PathVariable final String inseeCode, final Pageable pageable) {
		final var page = reviewService.findByCityInseeCode(inseeCode, pageable);
		return pagedResourcesAssembler.toModel(page);
	}

	@Operation(summary = "Word cloud data", description = "Word frequency map for generating a word cloud from city reviews")
	@GetMapping(WORD_CLOUD)
	public Map<String, Integer> getWordCloud(
			@Parameter(description = "City INSEE code") @PathVariable final String inseeCode) {
		return reviewService.getWordFrequencies(inseeCode);
	}

	@Operation(summary = "Sentiment statistics", description = "Aggregated sentiment analysis statistics for a city")
	@GetMapping(SENTIMENT_STATS)
	public SentimentStats getSentimentStats(
			@Parameter(description = "City INSEE code") @PathVariable final String inseeCode) {
		return reviewService.getSentimentStats(inseeCode);
	}
}
