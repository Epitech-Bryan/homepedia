package com.homepedia.api.service;

import com.homepedia.api.config.CacheConfig;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
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
public class CacheInvalidationService {

	/**
	 * Cache names exposed to the admin UI, in display order.
	 */
	public static final List<String> AVAILABLE_CACHES = List.of(CacheConfig.CACHE_GEO, CacheConfig.CACHE_REFDATA,
			CacheConfig.CACHE_STATS, CacheConfig.CACHE_REVIEWS);

	private final CacheManager cacheManager;

	public CacheInvalidationService(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

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

	/**
	 * Evict a single cache by its registered name (one of
	 * {@link #AVAILABLE_CACHES}). Resolved through the {@link CacheManager} rather
	 * than via @{@link CacheEvict} so the name can be supplied at runtime (e.g.
	 * from an admin endpoint).
	 *
	 * @return {@code true} if the cache existed and was cleared, {@code false}
	 *         otherwise
	 */
	public boolean evictByName(String name) {
		if (name == null || !AVAILABLE_CACHES.contains(name)) {
			return false;
		}
		final var cache = cacheManager.getCache(name);
		if (cache == null) {
			return false;
		}
		cache.clear();
		log.info("Evicted cache '{}' on demand", name);
		return true;
	}
}
