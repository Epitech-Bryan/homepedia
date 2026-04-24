package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.City.REVIEWS;

import com.homepedia.api.service.ReviewService;
import com.homepedia.common.review.ReviewSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reviews", description = "City reviews and sentiment analysis")
@RestController
@RequestMapping(REVIEWS)
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;
	private final PagedResourcesAssembler<ReviewSummary> pagedResourcesAssembler;

	@Operation(summary = "List reviews for a city", description = "Paginated reviews with sentiment data for a given city")
	@GetMapping
	public PagedModel<EntityModel<ReviewSummary>> findByCityInseeCode(
			@Parameter(description = "City INSEE code") @PathVariable final String inseeCode, final Pageable pageable) {
		final var page = reviewService.findByCityInseeCode(inseeCode, pageable);
		return pagedResourcesAssembler.toModel(page);
	}
}
