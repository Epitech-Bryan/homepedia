package com.homepedia.api.service;

import com.homepedia.api.config.CacheConfig;
import com.homepedia.api.mapper.RegionMapper;
import com.homepedia.common.region.RegionRepository;
import com.homepedia.common.region.RegionSummary;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

	private final RegionRepository regionRepository;

	@Cacheable(value = CacheConfig.CACHE_REFDATA, key = "'regions:all'")
	public List<RegionSummary> findAll() {
		return RegionMapper.INSTANCE.convertToSummaryList(regionRepository.findAll());
	}

	@Cacheable(value = CacheConfig.CACHE_REFDATA, key = "'region:' + #code", unless = "#result == null || !#result.isPresent()")
	public Optional<RegionSummary> findByCode(final String code) {
		return regionRepository.findByCode(code).map(RegionMapper.INSTANCE::convertToSummary);
	}
}
