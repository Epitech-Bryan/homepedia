package com.homepedia.api.service;

import com.homepedia.api.config.CacheConfig;
import com.homepedia.common.stats.CityStats;
import com.homepedia.common.stats.DepartmentStats;
import com.homepedia.common.stats.RegionStats;
import com.homepedia.common.stats.StatsRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

	/**
	 * Hard cap on a single batched city-stats query — keeps the SQL
	 * {@code IN (...)} list bounded and avoids accidental table scans.
	 */
	private static final int MAX_CITY_CODES = 2000;

	private final StatsRepository statsRepository;

	@Cacheable(value = CacheConfig.CACHE_STATS, key = "'regions'")
	public List<RegionStats> regionStats() {
		return statsRepository.aggregateRegionStats().stream()
				.map(p -> new RegionStats(p.getCode(), p.getName(), p.getPopulation(), p.getArea(),
						p.getTransactionCount(), p.getAveragePrice(), p.getAveragePricePerSqm()))
				.toList();
	}

	@Cacheable(value = CacheConfig.CACHE_STATS, key = "'departments:' + (#regionCode ?: 'all')")
	public List<DepartmentStats> departmentStats(String regionCode) {
		return statsRepository.aggregateDepartmentStats(regionCode).stream()
				.map(p -> new DepartmentStats(p.getCode(), p.getName(), p.getRegionCode(), p.getPopulation(),
						p.getArea(), p.getTransactionCount(), p.getAveragePrice(), p.getAveragePricePerSqm()))
				.toList();
	}

	/**
	 * Aggregate stats for a set of communes. Caching key sorts and joins the codes
	 * so the same viewport hits the same Redis entry regardless of the frontend's
	 * iteration order.
	 */
	@Cacheable(value = CacheConfig.CACHE_STATS, key = "'cities:' + #codes.stream().sorted().toList().toString()")
	public List<CityStats> cityStats(Collection<String> codes) {
		if (codes == null || codes.isEmpty()) {
			return List.of();
		}
		final var bounded = codes.stream().distinct().limit(MAX_CITY_CODES).toList();
		return statsRepository.aggregateCityStats(bounded).stream()
				.map(p -> new CityStats(p.getCode(), p.getName(), p.getDepartmentCode(), p.getPopulation(), p.getArea(),
						p.getTransactionCount(), p.getAveragePrice(), p.getAveragePricePerSqm()))
				.toList();
	}
}
