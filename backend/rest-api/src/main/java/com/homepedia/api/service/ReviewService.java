package com.homepedia.api.service;

import com.homepedia.api.config.CacheConfig;
import com.homepedia.api.mapper.ReviewMapper;
import com.homepedia.common.review.ReviewRepository;
import com.homepedia.common.review.ReviewSummary;
import com.homepedia.common.review.SentimentStats;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

	private static final int MIN_WORD_LENGTH = 4;
	private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

	private final ReviewRepository reviewRepository;

	public Page<ReviewSummary> findByCityInseeCode(final String cityInseeCode, final Pageable pageable) {
		return reviewRepository.findByCityInseeCode(cityInseeCode, pageable)
				.map(ReviewMapper.INSTANCE::convertToSummary);
	}

	@Cacheable(value = CacheConfig.CACHE_REVIEWS, key = "'wordcloud:' + #cityInseeCode")
	public Map<String, Integer> getWordFrequencies(final String cityInseeCode) {
		final var reviews = reviewRepository.findByCityInseeCode(cityInseeCode);
		final var frequencies = new HashMap<String, Integer>();

		for (final var review : CollectionUtils.emptyIfNull(reviews)) {
			if (StringUtils.isBlank(review.getContent())) {
				continue;
			}
			final var normalized = removeDiacritics(review.getContent().toLowerCase());
			final var words = normalized.split("[^a-zA-Z]+");
			for (final var word : words) {
				if (StringUtils.isNotBlank(word) && word.length() >= MIN_WORD_LENGTH) {
					frequencies.merge(word, 1, Integer::sum);
				}
			}
		}

		return frequencies;
	}

	@Cacheable(value = CacheConfig.CACHE_REVIEWS, key = "'sentiment:' + #cityInseeCode")
	public SentimentStats getSentimentStats(final String cityInseeCode) {
		final var reviews = reviewRepository.findByCityInseeCode(cityInseeCode);

		if (CollectionUtils.isEmpty(reviews)) {
			return new SentimentStats(0.0, 0, 0, 0, 0);
		}

		var totalScore = 0.0;
		var positiveCount = 0L;
		var negativeCount = 0L;
		var neutralCount = 0L;

		for (final var review : reviews) {
			final var score = review.getSentimentScore() != null ? review.getSentimentScore() : 0.0;
			totalScore += score;

			final var label = review.getSentimentLabel();
			if ("POSITIVE".equals(label)) {
				positiveCount++;
			} else if ("NEGATIVE".equals(label)) {
				negativeCount++;
			} else {
				neutralCount++;
			}
		}

		final var averageScore = Math.round((totalScore / reviews.size()) * 1000.0) / 1000.0;
		return new SentimentStats(averageScore, positiveCount, negativeCount, neutralCount, reviews.size());
	}

	private String removeDiacritics(final String input) {
		final var normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
		return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
	}
}
