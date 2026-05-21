package com.homepedia.api.service;

import com.homepedia.api.mapper.CityMapper;
import com.homepedia.common.city.CityRepository;
import com.homepedia.common.city.CitySummary;
import com.homepedia.common.stats.QuarterlyPricePoint;
import com.homepedia.common.stats.QuarterlyPriceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.homepedia.api.config.CacheConfig.CACHE_STATS;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {

	private final CityRepository cityRepository;
	private final QuarterlyPriceRepository quarterlyPriceRepository;

	public Page<CitySummary> findAll(final String departmentCode, final String query, final Pageable pageable) {
		if (StringUtils.isNotBlank(query)) {
			return cityRepository.searchByName(query, pageable).map(CityMapper.INSTANCE::convertToSummary);
		}
		if (StringUtils.isNotBlank(departmentCode)) {
			return cityRepository.findByDepartmentCode(departmentCode, pageable)
					.map(CityMapper.INSTANCE::convertToSummary);
		}
		return cityRepository.findAll(pageable).map(CityMapper.INSTANCE::convertToSummary);
	}

	public Optional<CitySummary> findByInseeCode(final String inseeCode) {
		return cityRepository.findByInseeCode(inseeCode).map(CityMapper.INSTANCE::convertToSummary);
	}

	@Cacheable(value = CACHE_STATS, key = "'price-history:' + #inseeCode")
	public List<QuarterlyPricePoint> priceHistory(final String inseeCode) {
		return quarterlyPriceRepository.findTimelineByInsee(inseeCode).stream()
				.map(p -> new QuarterlyPricePoint(p.getYear(), p.getQuarter(),
						p.getTransactionCount() != null ? p.getTransactionCount() : 0, p.getAveragePricePerSqm()))
				.toList();
	}
}
