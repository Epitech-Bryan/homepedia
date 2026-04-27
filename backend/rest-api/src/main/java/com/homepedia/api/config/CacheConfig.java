package com.homepedia.api.config;

import java.time.Duration;
import java.util.Map;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Spring cache backed by Redis. Each named cache has its own TTL based on how
 * volatile the underlying data is:
 * <ul>
 * <li><code>geo</code>: GeoJSON polygons — almost never change</li>
 * <li><code>refdata</code>: regions/departments lists — change once a year
 * max</li>
 * <li><code>stats</code>: aggregates rebuilt by batch jobs — invalidated on
 * import via {@code @CacheEvict}</li>
 * <li><code>reviews</code>: word clouds, sentiment aggregates — moderate
 * volatility</li>
 * </ul>
 *
 * <p>
 * All keys are prefixed with {@code homepedia:} so this app can safely share a
 * Redis instance with unrelated services.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

	public static final String CACHE_GEO = "geo";
	public static final String CACHE_REFDATA = "refdata";
	public static final String CACHE_STATS = "stats";
	public static final String CACHE_REVIEWS = "reviews";

	private static final String KEY_PREFIX = "homepedia:";

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		final var ptv = BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build();
		final ObjectMapper mapper = JsonMapper.builder().findAndAddModules()
				.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING)
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
		final var jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);

		final var defaults = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(1))
				.prefixCacheNameWith(KEY_PREFIX)
				.serializeKeysWith(
						RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
				.disableCachingNullValues();

		final var perCache = Map.of(CACHE_GEO, defaults.entryTtl(Duration.ofHours(24)), CACHE_REFDATA,
				defaults.entryTtl(Duration.ofHours(12)), CACHE_STATS, defaults.entryTtl(Duration.ofMinutes(30)),
				CACHE_REVIEWS, defaults.entryTtl(Duration.ofMinutes(15)));

		final var manager = RedisCacheManager.builder(connectionFactory).cacheDefaults(defaults)
				.withInitialCacheConfigurations(perCache).transactionAware().build();

		flushStaleEntries(connectionFactory);

		return manager;
	}

	private void flushStaleEntries(RedisConnectionFactory connectionFactory) {
		try {
			final var connection = connectionFactory.getConnection();
			final var keys = connection.keyCommands().keys((KEY_PREFIX + "*").getBytes());
			if (keys != null && !keys.isEmpty()) {
				connection.keyCommands().del(keys.toArray(byte[][]::new));
				log.info("Flushed {} stale Redis cache entries on startup", keys.size());
			}
			connection.close();
		} catch (Exception e) {
			log.warn("Could not flush Redis cache on startup: {}", e.getMessage());
		}
	}

	/**
	 * Swallow Redis errors so a Redis outage degrades to direct method execution
	 * instead of HTTP 500. The first failure logs at WARN; further failures are
	 * silent.
	 */
	@Override
	public CacheErrorHandler errorHandler() {
		return new CacheErrorHandler() {
			@Override
			public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
				log.warn("Redis GET failed for cache={} key={}: {}", cache.getName(), key, ex.getMessage());
			}

			@Override
			public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
				log.warn("Redis PUT failed for cache={} key={}: {}", cache.getName(), key, ex.getMessage());
			}

			@Override
			public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
				log.warn("Redis EVICT failed for cache={} key={}: {}", cache.getName(), key, ex.getMessage());
			}

			@Override
			public void handleCacheClearError(RuntimeException ex, Cache cache) {
				log.warn("Redis CLEAR failed for cache={}: {}", cache.getName(), ex.getMessage());
			}
		};
	}
}
