package com.homepedia.api.service;

import com.homepedia.api.config.CacheConfig;
import com.homepedia.api.mapper.ReviewMapper;
import com.homepedia.common.city.CityRepository;
import com.homepedia.common.review.CityReview;
import com.homepedia.common.review.ReviewRepository;
import com.homepedia.common.review.ReviewSummary;
import com.homepedia.common.review.SentimentStats;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rolls the per-commune reviews stored in MongoDB up to a department, region or
 * whole-country scope. Reviews only carry a {@code cityInseeCode}, so the scope
 * is first resolved to the set of INSEE codes under it (via the relational
 * cities table) and then queried with a single {@code $in} — except for the
 * country scope, where a 35 k-element {@code $in} is worse than a plain scan
 * and we omit the match stage entirely.
 *
 * <p>
 * Sentiment is computed with a MongoDB {@code $group} so we never stream
 * hundreds of thousands of documents into the JVM. The word cloud samples the
 * matching reviews ({@link #WORD_CLOUD_SAMPLE}) before tokenising — the review
 * corpus is generated from a small fixed template set, so a sample is fully
 * representative while keeping the national word cloud bounded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AreaReviewService {

	/**
	 * Geographic scope a review roll-up can be requested for. {@code COUNTRY}
	 * aggregates every commune (no {@code $in} filter).
	 */
	public enum AreaLevel {
		REGION, DEPARTMENT, COUNTRY
	}

	private static final int MIN_WORD_LENGTH = 4;
	// Upper bound on documents fed to the word-cloud tokeniser. Reviews come
	// from a fixed set of ~30 templates, so this is representative at every
	// scale while capping the national roll-up's cost.
	private static final int WORD_CLOUD_SAMPLE = 8000;
	private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

	private final ReviewRepository reviewRepository;
	private final CityRepository cityRepository;
	private final MongoTemplate mongoTemplate;

	/**
	 * Resolve a scope to its set of commune INSEE codes, or {@code null} for the
	 * country scope (meaning "every commune", queried without a filter).
	 */
	private Collection<String> resolveCodes(final AreaLevel level, final String code) {
		return switch (level) {
			case REGION -> cityRepository.findInseeCodesByRegionCode(code);
			case DEPARTMENT -> cityRepository.findInseeCodesByDepartmentCode(code);
			case COUNTRY -> null;
		};
	}

	public Page<ReviewSummary> reviews(final AreaLevel level, final String code, final Pageable pageable) {
		final var codes = resolveCodes(level, code);
		final Page<CityReview> page = codes == null
				? reviewRepository.findAll(pageable)
				: reviewRepository.findByCityInseeCodeIn(codes, pageable);
		return page.map(ReviewMapper.INSTANCE::convertToSummary);
	}

	@Cacheable(value = CacheConfig.CACHE_REVIEWS, key = "'area-wordcloud:' + #level + ':' + #code")
	public Map<String, Integer> wordFrequencies(final AreaLevel level, final String code) {
		final var codes = resolveCodes(level, code);
		final var ops = new java.util.ArrayList<AggregationOperation>();
		if (codes != null) {
			ops.add(Aggregation.match(Criteria.where("cityInseeCode").in(codes)));
		}
		ops.add(Aggregation.sample(WORD_CLOUD_SAMPLE));
		ops.add(Aggregation.project("content"));
		final var agg = Aggregation.newAggregation(CityReview.class, ops);
		final var docs = mongoTemplate.aggregate(agg, CityReview.class, Document.class).getMappedResults();

		final var frequencies = new HashMap<String, Integer>();
		for (final var doc : docs) {
			final var content = doc.getString("content");
			if (StringUtils.isBlank(content)) {
				continue;
			}
			final var normalized = removeDiacritics(content.toLowerCase());
			for (final var word : normalized.split("[^a-zA-Z]+")) {
				if (StringUtils.isNotBlank(word) && word.length() >= MIN_WORD_LENGTH) {
					frequencies.merge(word, 1, Integer::sum);
				}
			}
		}
		return frequencies;
	}

	@Cacheable(value = CacheConfig.CACHE_REVIEWS, key = "'area-sentiment:' + #level + ':' + #code")
	public SentimentStats sentimentStats(final AreaLevel level, final String code) {
		final var codes = resolveCodes(level, code);
		final var ops = new java.util.ArrayList<AggregationOperation>();
		if (codes != null) {
			ops.add(Aggregation.match(Criteria.where("cityInseeCode").in(codes)));
		}
		ops.add(Aggregation.group("sentimentLabel").count().as("count").sum("sentimentScore").as("scoreSum"));
		final var agg = Aggregation.newAggregation(CityReview.class, ops);
		final List<Document> rows = mongoTemplate.aggregate(agg, CityReview.class, Document.class).getMappedResults();

		var positive = 0L;
		var negative = 0L;
		var neutral = 0L;
		var total = 0L;
		var totalScore = 0.0;
		for (final var row : rows) {
			final var label = row.getString("_id");
			final var count = toLong(row.get("count"));
			total += count;
			totalScore += toDouble(row.get("scoreSum"));
			if ("POSITIVE".equals(label)) {
				positive += count;
			} else if ("NEGATIVE".equals(label)) {
				negative += count;
			} else {
				neutral += count;
			}
		}

		if (total == 0) {
			return new SentimentStats(0.0, 0, 0, 0, 0);
		}
		final var averageScore = Math.round((totalScore / total) * 1000.0) / 1000.0;
		return new SentimentStats(averageScore, positive, negative, neutral, total);
	}

	private static long toLong(final Object value) {
		return value instanceof Number n ? n.longValue() : 0L;
	}

	private static double toDouble(final Object value) {
		return value instanceof Number n ? n.doubleValue() : 0.0;
	}

	private String removeDiacritics(final String input) {
		final var normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
		return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
	}
}
