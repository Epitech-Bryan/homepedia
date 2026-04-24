package com.homepedia.api.service;

import com.homepedia.api.mapper.IndicatorMapper;
import com.homepedia.common.indicator.GeographicLevel;
import com.homepedia.common.indicator.IndicatorCategory;
import com.homepedia.common.indicator.IndicatorRepository;
import com.homepedia.common.indicator.IndicatorSummary;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndicatorService {

	private final IndicatorRepository indicatorRepository;

	public List<IndicatorSummary> findIndicators(final GeographicLevel level, final String code,
			final IndicatorCategory category) {
		final var indicators = (category != null)
				? indicatorRepository.findByGeographicLevelAndGeographicCodeAndCategory(level, code, category)
				: indicatorRepository.findByGeographicLevelAndGeographicCode(level, code);
		return IndicatorMapper.INSTANCE.convertToSummaryList(indicators);
	}
}
