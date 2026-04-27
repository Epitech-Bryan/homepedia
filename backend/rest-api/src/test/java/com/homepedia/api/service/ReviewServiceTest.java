package com.homepedia.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.homepedia.common.review.CityReview;
import com.homepedia.common.review.ReviewRepository;
import java.util.Collections;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

	@Mock
	private ReviewRepository reviewRepository;

	@InjectMocks
	private ReviewService reviewService;

	@Test
	void getWordFrequencies_withReviews_extractsWordsCorrectly() {
		final var review1 = CityReview.builder().content("Belle ville avec beaucoup de charme").build();
		final var review2 = CityReview.builder().content("Très belle ville calme").build();

		when(reviewRepository.findByCityInseeCode("75056")).thenReturn(List.of(review1, review2));

		final var frequencies = reviewService.getWordFrequencies("75056");

		assertThat(frequencies).containsEntry("belle", 2);
		assertThat(frequencies).containsEntry("ville", 2);
		assertThat(frequencies).containsEntry("beaucoup", 1);
		assertThat(frequencies).containsEntry("charme", 1);
		assertThat(frequencies).containsEntry("calme", 1);
		assertThat(frequencies).doesNotContainKey("de");
		assertThat(frequencies).containsEntry("avec", 1);
	}

	@Test
	void getWordFrequencies_emptyReviews_returnsEmptyMap() {
		when(reviewRepository.findByCityInseeCode("99999")).thenReturn(Collections.emptyList());

		final var frequencies = reviewService.getWordFrequencies("99999");

		assertThat(frequencies).isEmpty();
	}

	@Test
	void getWordFrequencies_reviewWithBlankContent_skipsIt() {
		final var blankReview = CityReview.builder().content("").build();
		final var nullReview = CityReview.builder().content(null).build();
		final var validReview = CityReview.builder().content("magnifique endroit").build();

		when(reviewRepository.findByCityInseeCode("75056")).thenReturn(List.of(blankReview, nullReview, validReview));

		final var frequencies = reviewService.getWordFrequencies("75056");

		assertThat(frequencies).containsEntry("magnifique", 1);
		assertThat(frequencies).containsEntry("endroit", 1);
		assertThat(frequencies).hasSize(2);
	}

	@Test
	void getWordFrequencies_removesDiacritics() {
		final var review = CityReview.builder().content("résidentiel éloigné général").build();

		when(reviewRepository.findByCityInseeCode("75056")).thenReturn(List.of(review));

		final var frequencies = reviewService.getWordFrequencies("75056");

		assertThat(frequencies).containsKey("residentiel");
		assertThat(frequencies).containsKey("eloigne");
		assertThat(frequencies).containsKey("general");
		assertThat(frequencies).doesNotContainKey("résidentiel");
	}

	@Test
	void getSentimentStats_withMixedSentiments_calculatesCorrectly() {
		final var positive = CityReview.builder().sentimentScore(0.8).sentimentLabel("POSITIVE").build();
		final var negative = CityReview.builder().sentimentScore(-0.5).sentimentLabel("NEGATIVE").build();
		final var neutral = CityReview.builder().sentimentScore(0.1).sentimentLabel("NEUTRAL").build();

		when(reviewRepository.findByCityInseeCode("75056")).thenReturn(List.of(positive, negative, neutral));

		final var stats = reviewService.getSentimentStats("75056");

		assertThat(stats.totalReviews()).isEqualTo(3);
		assertThat(stats.positiveCount()).isEqualTo(1);
		assertThat(stats.negativeCount()).isEqualTo(1);
		assertThat(stats.neutralCount()).isEqualTo(1);
		assertThat(stats.averageScore()).isCloseTo(0.133, Offset.offset(0.001));
	}

	@Test
	void getSentimentStats_emptyReviews_returnsZeroStats() {
		when(reviewRepository.findByCityInseeCode("99999")).thenReturn(Collections.emptyList());

		final var stats = reviewService.getSentimentStats("99999");

		assertThat(stats.totalReviews()).isZero();
		assertThat(stats.positiveCount()).isZero();
		assertThat(stats.negativeCount()).isZero();
		assertThat(stats.neutralCount()).isZero();
		assertThat(stats.averageScore()).isEqualTo(0.0);
	}

	@Test
	void getSentimentStats_nullSentimentScore_treatsAsZero() {
		final var review = CityReview.builder().sentimentScore(null).sentimentLabel("NEUTRAL").build();

		when(reviewRepository.findByCityInseeCode("75056")).thenReturn(List.of(review));

		final var stats = reviewService.getSentimentStats("75056");

		assertThat(stats.totalReviews()).isEqualTo(1);
		assertThat(stats.averageScore()).isEqualTo(0.0);
		assertThat(stats.neutralCount()).isEqualTo(1);
	}

	@Test
	void getSentimentStats_unknownLabel_countsAsNeutral() {
		final var review = CityReview.builder().sentimentScore(0.3).sentimentLabel("UNKNOWN").build();

		when(reviewRepository.findByCityInseeCode("75056")).thenReturn(List.of(review));

		final var stats = reviewService.getSentimentStats("75056");

		assertThat(stats.neutralCount()).isEqualTo(1);
		assertThat(stats.positiveCount()).isZero();
		assertThat(stats.negativeCount()).isZero();
	}
}
