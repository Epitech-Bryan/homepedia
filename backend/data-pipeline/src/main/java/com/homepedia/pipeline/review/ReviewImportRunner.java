package com.homepedia.pipeline.review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ReviewImportRunner implements CommandLineRunner {

	private final ReviewScraperService reviewScraperService;

	@Value("${homepedia.import.reviews.enabled:false}")
	private boolean importEnabled;

	@Override
	public void run(final String... args) {
		if (!importEnabled) {
			log.info("Review import is disabled (homepedia.import.reviews.enabled=false). Skipping.");
			return;
		}

		log.info("Starting review data import...");
		reviewScraperService.importReviews();
		log.info("Review data import finished.");
	}
}
