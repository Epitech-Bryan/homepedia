package com.homepedia.api.service;

import com.homepedia.api.config.CacheConfig;
import com.homepedia.common.stats.DepartmentStats;
import com.homepedia.common.stats.RegionStats;
import com.homepedia.common.stats.StatsRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

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
}
