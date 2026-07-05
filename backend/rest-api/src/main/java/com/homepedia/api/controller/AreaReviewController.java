package com.homepedia.api.controller;

import com.homepedia.api.constant.HomepediaConstant.RestPath.AreaReview;
import com.homepedia.api.service.AreaReviewService;
import com.homepedia.api.service.AreaReviewService.AreaLevel;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Review roll-ups above the commune level. Same three views as the per-city
 * {@link ReviewController} (paginated reviews, word cloud, sentiment) but
 * scoped to a department, a region or the whole country so a click on any of
 * those on the map surfaces the aggregated resident opinion.
 */
@Tag(name = "Area reviews", description = "Aggregated reviews and sentiment for departments, regions and the country")
@RestController
@RequiredArgsConstructor
public class AreaReviewController {

	private final AreaReviewService areaReviewService;
	private final PagedResourcesAssembler<ReviewSummary> pagedResourcesAssembler;

	// ---- Region ----------------------------------------------------------

	@Operation(summary = "Aggregated reviews for a region")
	@GetMapping(AreaReview.REGION_REVIEWS)
	public ResponseEntity<PagedModel<EntityModel<ReviewSummary>>> regionReviews(
			@Parameter(description = "Region code") @PathVariable final String code, final Pageable pageable) {
		final var page = areaReviewService.reviews(AreaLevel.REGION, code, pageable);
		return ResponseEntity.ok(pagedResourcesAssembler.toModel(page));
	}

	@Operation(summary = "Word cloud aggregated over a region's reviews")
	@GetMapping(AreaReview.REGION_WORD_CLOUD)
	public ResponseEntity<Map<String, Integer>> regionWordCloud(@PathVariable final String code) {
		return ResponseEntity.ok(areaReviewService.wordFrequencies(AreaLevel.REGION, code));
	}

	@Operation(summary = "Sentiment stats aggregated over a region's reviews")
	@GetMapping(AreaReview.REGION_SENTIMENT_STATS)
	public ResponseEntity<SentimentStats> regionSentiment(@PathVariable final String code) {
		return ResponseEntity.ok(areaReviewService.sentimentStats(AreaLevel.REGION, code));
	}

	// ---- Department ------------------------------------------------------

	@Operation(summary = "Aggregated reviews for a department")
	@GetMapping(AreaReview.DEPARTMENT_REVIEWS)
	public ResponseEntity<PagedModel<EntityModel<ReviewSummary>>> departmentReviews(
			@Parameter(description = "Department code") @PathVariable final String code, final Pageable pageable) {
		final var page = areaReviewService.reviews(AreaLevel.DEPARTMENT, code, pageable);
		return ResponseEntity.ok(pagedResourcesAssembler.toModel(page));
	}

	@Operation(summary = "Word cloud aggregated over a department's reviews")
	@GetMapping(AreaReview.DEPARTMENT_WORD_CLOUD)
	public ResponseEntity<Map<String, Integer>> departmentWordCloud(@PathVariable final String code) {
		return ResponseEntity.ok(areaReviewService.wordFrequencies(AreaLevel.DEPARTMENT, code));
	}

	@Operation(summary = "Sentiment stats aggregated over a department's reviews")
	@GetMapping(AreaReview.DEPARTMENT_SENTIMENT_STATS)
	public ResponseEntity<SentimentStats> departmentSentiment(@PathVariable final String code) {
		return ResponseEntity.ok(areaReviewService.sentimentStats(AreaLevel.DEPARTMENT, code));
	}

	// ---- Country ---------------------------------------------------------

	@Operation(summary = "Aggregated reviews for the whole country")
	@GetMapping(AreaReview.COUNTRY_REVIEWS)
	public ResponseEntity<PagedModel<EntityModel<ReviewSummary>>> countryReviews(final Pageable pageable) {
		final var page = areaReviewService.reviews(AreaLevel.COUNTRY, null, pageable);
		return ResponseEntity.ok(pagedResourcesAssembler.toModel(page));
	}

	@Operation(summary = "Word cloud aggregated over the whole country's reviews")
	@GetMapping(AreaReview.COUNTRY_WORD_CLOUD)
	public ResponseEntity<Map<String, Integer>> countryWordCloud() {
		return ResponseEntity.ok(areaReviewService.wordFrequencies(AreaLevel.COUNTRY, null));
	}

	@Operation(summary = "Sentiment stats aggregated over the whole country's reviews")
	@GetMapping(AreaReview.COUNTRY_SENTIMENT_STATS)
	public ResponseEntity<SentimentStats> countrySentiment() {
		return ResponseEntity.ok(areaReviewService.sentimentStats(AreaLevel.COUNTRY, null));
	}
}
