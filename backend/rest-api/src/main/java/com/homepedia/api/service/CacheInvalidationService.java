package com.homepedia.api.service;

import com.homepedia.api.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 * Centralized cache invalidation. Batch import services call into this after a
 * successful run so the next API request rebuilds the cached projection from
 * fresh data.
 *
 * <p>
 * Kept separate from the import services to avoid Spring AOP self-invocation
 * pitfalls — the @{@link CacheEvict} annotation only fires when the call goes
 * through a proxied bean reference.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheInvalidationService {

	@Caching(evict = {@CacheEvict(value = CacheConfig.CACHE_REFDATA, allEntries = true),
			@CacheEvict(value = CacheConfig.CACHE_GEO, allEntries = true),
			@CacheEvict(value = CacheConfig.CACHE_STATS, allEntries = true)})
	public void evictGeoAndRefdataAndStats() {
		log.info("Evicted refdata + geo + stats caches");
	}

	@CacheEvict(value = CacheConfig.CACHE_STATS, allEntries = true)
	public void evictStats() {
		log.info("Evicted stats cache");
	}

	@CacheEvict(value = CacheConfig.CACHE_REVIEWS, allEntries = true)
	public void evictReviews() {
		log.info("Evicted reviews cache");
	}

	public void evictAll() {
		evictGeoAndRefdataAndStats();
		evictReviews();
	}
}
