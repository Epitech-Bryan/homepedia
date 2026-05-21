package com.homepedia.api.service;

import static com.homepedia.api.config.CacheConfig.CACHE_STATS;

import com.homepedia.api.mapper.IndicatorMapper;
import com.homepedia.common.indicator.GeographicLevel;
import com.homepedia.common.indicator.IndicatorCategory;
import com.homepedia.common.indicator.IndicatorRepository;
import com.homepedia.common.indicator.IndicatorSummary;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndicatorService {

	private static final Pattern COMMUNE_INSEE = Pattern.compile("^[0-9AB]{5}$");

	private final IndicatorRepository indicatorRepository;

	public List<IndicatorSummary> findIndicators(final GeographicLevel level, final String code,
			final IndicatorCategory category) {
		final var indicators = (category != null)
				? indicatorRepository.findByGeographicLevelAndGeographicCodeAndCategory(level, code, category)
				: indicatorRepository.findByGeographicLevelAndGeographicCode(level, code);
		return IndicatorMapper.INSTANCE.convertToSummaryList(indicators);
	}

	/**
	 * Every IRIS-level indicator whose code lives under the given commune. Empty
	 * until the INSEE Filosofi importer (issue #10) seeds the table.
	 *
	 * <p>
	 * The query is a {@code LIKE :insee || '%'} probe served by the
	 * {@code idx_indicator_iris_code_prefix} index (changeset 016, opclassed with
	 * {@code varchar_pattern_ops} so it works under non-C collations). We refuse
	 * any code that isn't exactly 5 chars to avoid a degenerate {@code LIKE '%'}
	 * pattern that would scan every IRIS row in the country.
	 */
	@Cacheable(value = CACHE_STATS, key = "'iris-indicators:' + #communeInseeCode")
	public List<IndicatorSummary> findIrisIndicatorsByCommune(final String communeInseeCode) {
		if (communeInseeCode == null || !COMMUNE_INSEE.matcher(communeInseeCode).matches()) {
			return List.of();
		}
		return IndicatorMapper.INSTANCE
				.convertToSummaryList(indicatorRepository.findIrisIndicatorsByCommune(communeInseeCode));
	}
}
