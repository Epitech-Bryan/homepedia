package com.homepedia.api.batch.review;

import com.homepedia.common.city.CityRepository;
import com.homepedia.common.review.CityReview;
import com.homepedia.common.review.ReviewRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewScraperService {

	private static final int BATCH_SIZE = 2000;
	private static final int GENERATION_THREADS = 4;

	private final CityRepository cityRepository;
	private final ReviewRepository reviewRepository;
	private final ReviewDataGenerator reviewDataGenerator;
	private final SentimentAnalysisService sentimentAnalysisService;

	// No @Transactional: cities are read once into memory at the start, and
	// reviewRepository writes go to MongoDB (different store, not bound to
	// the JPA transaction manager anyway). Wrapping the whole loop in a
	// JPA transaction kept a Postgres connection idle-in-transaction holding
	// a RowShareLock on `cities`, which blocked DROP/ALTER on tables with
	// FKs to cities (e.g. partition swaps during DVF imports).
	public void importReviews() {
		final var cities = cityRepository.findAll();
		log.info("Generating reviews for {} cities", cities.size());

		// Generation + sentiment analysis are pure CPU work. Run on a
		// dedicated thread pool so the rest of the cluster's CPU isn't
		// monopolized but we still get a meaningful speedup on the ~350k
		// generated reviews.
		final ExecutorService executor = Executors.newFixedThreadPool(GENERATION_THREADS);
		final List<CityReview> allReviews;
		try {
			final List<CompletableFuture<List<CityReview>>> futures = cities.stream()
					.map(city -> CompletableFuture.supplyAsync(() -> generateForCity(city.getInseeCode()), executor))
					.toList();
			allReviews = futures.stream().map(CompletableFuture::join).flatMap(List::stream).toList();
		} finally {
			executor.shutdown();
		}
		log.info("Generated {} reviews, persisting in batches of {}...", allReviews.size(), BATCH_SIZE);

		var totalCount = 0;
		for (int i = 0; i < allReviews.size(); i += BATCH_SIZE) {
			final var batch = allReviews.subList(i, Math.min(i + BATCH_SIZE, allReviews.size()));
			reviewRepository.saveAll(batch);
			totalCount += batch.size();
			log.info("Saved {} / {} reviews...", totalCount, allReviews.size());
		}

		log.info("Review import finished. Total reviews: {}", totalCount);
	}

	private List<CityReview> generateForCity(final String inseeCode) {
		final var generated = reviewDataGenerator.generateForCity(inseeCode);
		final var result = new ArrayList<CityReview>(generated.size());
		for (final var gen : generated) {
			final var sentiment = sentimentAnalysisService.analyze(gen.content());
			result.add(CityReview.builder().cityInseeCode(gen.cityInseeCode()).content(gen.content())
					.rating(gen.rating()).author(gen.author()).publishedAt(gen.publishedAt())
					.sentimentScore(sentiment.score()).sentimentLabel(sentiment.label()).build());
		}
		return result;
	}
}
