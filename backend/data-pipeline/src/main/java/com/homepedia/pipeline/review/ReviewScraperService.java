package com.homepedia.pipeline.review;

import com.homepedia.common.city.CityRepository;
import com.homepedia.common.review.CityReview;
import com.homepedia.common.review.ReviewRepository;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewScraperService {

	private static final int BATCH_SIZE = 500;

	private final CityRepository cityRepository;
	private final ReviewRepository reviewRepository;
	private final ReviewDataGenerator reviewDataGenerator;
	private final SentimentAnalysisService sentimentAnalysisService;

	@Transactional
	public void importReviews() {
		final var cities = cityRepository.findAll();
		log.info("Generating reviews for {} cities", cities.size());

		final var batch = new ArrayList<CityReview>(BATCH_SIZE);
		var totalCount = 0;

		for (final var city : cities) {
			final var generated = reviewDataGenerator.generateForCity(city.getInseeCode());

			for (final var gen : generated) {
				final var sentiment = sentimentAnalysisService.analyze(gen.content());
				final var review = CityReview.builder().cityInseeCode(gen.cityInseeCode()).content(gen.content())
						.rating(gen.rating()).author(gen.author()).publishedAt(gen.publishedAt())
						.sentimentScore(sentiment.score()).sentimentLabel(sentiment.label()).build();

				batch.add(review);
			}

			if (batch.size() >= BATCH_SIZE) {
				reviewRepository.saveAll(batch);
				totalCount += batch.size();
				batch.clear();
				log.info("Saved {} reviews...", totalCount);
			}
		}

		if (!batch.isEmpty()) {
			reviewRepository.saveAll(batch);
			totalCount += batch.size();
		}

		log.info("Review import finished. Total reviews: {}", totalCount);
	}
}
