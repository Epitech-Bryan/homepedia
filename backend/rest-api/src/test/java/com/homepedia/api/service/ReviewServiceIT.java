package com.homepedia.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homepedia.api.TestcontainersConfig;
import com.homepedia.common.review.CityReview;
import com.homepedia.common.review.ReviewRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;

import static com.homepedia.api.config.CacheConfig.CACHE_REVIEWS;

/**
 * MongoDB-backed integration test for {@link ReviewService} — closes issue #1.
 *
 * <p>
 * The unit-test layer mocks {@link ReviewRepository}, so a regression in the
 * Mongo query path (index miss, projection mismatch, serialisation change)
 * would only show up in prod. This test seeds a real MongoDB testcontainer with
 * deterministic review documents and asserts that the two {@code @Cacheable}
 * methods compute their values against the live driver round-trip exactly the
 * same way they do offline.
 *
 * <p>
 * The {@link CacheManager} is cleared in {@link #clearCache()} between tests
 * because the {@code @Cacheable} annotation otherwise serves the first test's
 * computation to every subsequent assertion (and we want each scenario to
 * exercise the Mongo query, not the cache).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ReviewServiceIT {

	private static final String CITY_INSEE = "75056";

	private static final String OTHER_CITY_INSEE = "13055";

	@Autowired
	private ReviewService reviewService;

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private CacheManager cacheManager;

	@AfterEach
	void clearCache() {
		reviewRepository.deleteAll();
		final var cache = cacheManager.getCache(CACHE_REVIEWS);
		if (cache != null) {
			cache.clear();
		}
	}

	@Test
	void getWordFrequencies_buildsFrequencyMapFromMongoDocuments() {
		seedReview(CITY_INSEE, "Quartier calme avec beaucoup de commerces. Tres calme.", "POSITIVE", 0.8);
		seedReview(CITY_INSEE, "Beaucoup trop bruyant le soir, sinon calme la journee.", "NEUTRAL", 0.1);

		final var frequencies = reviewService.getWordFrequencies(CITY_INSEE);

		// MIN_WORD_LENGTH=4 means "soir" is in but "trop" is in too; "tres"
		// (4 chars after diacritic strip) lands too. "de" / "la" / "et" are
		// filtered out. Counts are case-insensitive after the lower + strip.
		assertThat(frequencies).containsEntry("calme", 3).containsEntry("beaucoup", 2);
		assertThat(frequencies.keySet()).noneMatch(k -> k.length() < 4);
	}

	@Test
	void getWordFrequencies_skipsOtherCitiesAndBlankContent() {
		seedReview(CITY_INSEE, "Charmant village", "POSITIVE", 0.6);
		seedReview(OTHER_CITY_INSEE, "Charmant aussi mais pas ici", "POSITIVE", 0.7);
		seedReview(CITY_INSEE, "   ", "NEUTRAL", 0.0);
		seedReview(CITY_INSEE, null, "NEUTRAL", 0.0);

		final var frequencies = reviewService.getWordFrequencies(CITY_INSEE);

		assertThat(frequencies).containsEntry("charmant", 1).containsEntry("village", 1);
		assertThat(frequencies).doesNotContainKey("aussi");
	}

	@Test
	void getSentimentStats_aggregatesLabelsAndAverages() {
		seedReview(CITY_INSEE, "Genial", "POSITIVE", 0.9);
		seedReview(CITY_INSEE, "Pas terrible", "NEGATIVE", -0.4);
		seedReview(CITY_INSEE, "Bof", "NEUTRAL", 0.0);
		seedReview(CITY_INSEE, "Excellent quartier", "POSITIVE", 0.7);

		final var stats = reviewService.getSentimentStats(CITY_INSEE);

		assertThat(stats.totalReviews()).isEqualTo(4);
		assertThat(stats.positiveCount()).isEqualTo(2);
		assertThat(stats.negativeCount()).isEqualTo(1);
		assertThat(stats.neutralCount()).isEqualTo(1);
		// (0.9 + -0.4 + 0.0 + 0.7) / 4 = 0.3 (rounded to 3 decimals)
		assertThat(stats.averageScore()).isEqualTo(0.3);
	}

	@Test
	void getSentimentStats_returnsZeroedRecordWhenCityHasNoReviews() {
		seedReview(OTHER_CITY_INSEE, "Genial", "POSITIVE", 0.9);

		final var stats = reviewService.getSentimentStats(CITY_INSEE);

		assertThat(stats.totalReviews()).isZero();
		assertThat(stats.positiveCount()).isZero();
		assertThat(stats.negativeCount()).isZero();
		assertThat(stats.neutralCount()).isZero();
		assertThat(stats.averageScore()).isZero();
	}

	@Test
	void findByCityInseeCode_returnsOnlyReviewsForTheGivenCity() {
		seedReview(CITY_INSEE, "Paris", "POSITIVE", 0.6);
		seedReview(CITY_INSEE, "Encore Paris", "POSITIVE", 0.5);
		seedReview(OTHER_CITY_INSEE, "Marseille", "NEUTRAL", 0.0);

		final var page = reviewService.findByCityInseeCode(CITY_INSEE,
				org.springframework.data.domain.PageRequest.of(0, 50));

		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent()).allMatch(r -> r.content().contains("Paris"));
	}

	private void seedReview(final String cityInsee, final String content, final String label, final double score) {
		reviewRepository.save(CityReview.builder().cityInseeCode(cityInsee).content(content).sentimentLabel(label)
				.sentimentScore(score).publishedAt(LocalDate.now()).author("test").rating(4.0).build());
	}
}
