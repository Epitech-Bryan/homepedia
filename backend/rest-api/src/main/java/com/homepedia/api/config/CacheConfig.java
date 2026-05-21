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

	// Bump the version suffix whenever the on-disk serialisation format
	// changes (Jackson typing strategy, value class shape, etc.) so the
	// new pod reads/writes in a fresh namespace instead of trying to
	// deserialise the previous format. Old keys expire naturally via TTL
	// (max 24h on geo); flushStaleEntries below also tries to clean them
	// on boot but is best-effort.
	private static final String KEY_PREFIX = "homepedia:v2:";

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		// Jackson 2.18 deprecated DefaultTyping.EVERYTHING in favour of
		// NON_FINAL_AND_ENUMS, but switching emits records (and any other
		// implicitly-final class) WITHOUT a type wrapper, while EVERYTHING
		// wrapped them. flushStaleEntries below is meant to wipe the
		// previous-format cache on boot — in practice that didn't catch
		// every key under @Cacheable, and a subset of stats / refdata
		// payloads stayed in the old format and failed to deserialise on
		// the next read ("Unexpected token (START_ARRAY), expected
		// VALUE_STRING"). The webapp surfaced the failure as empty stats
		// everywhere, so we stick to EVERYTHING until the cache flush is
		// rebuilt around an explicit format-version marker.
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

	// Cleans any keys that still live under previous KEY_PREFIX versions
	// (the suffix is bumped whenever the serialisation format changes).
	// Logs at INFO even when 0 keys are found so we can tell apart "flush
	// ran and matched nothing" from "flush silently failed", which is
	// what made the v1→v2 cache migration painful to diagnose.
	private void flushStaleEntries(RedisConnectionFactory connectionFactory) {
		final var staleRoot = "homepedia:";
		try (final var connection = connectionFactory.getConnection()) {
			final var keys = connection.keyCommands().keys((staleRoot + "*").getBytes());
			final var matched = keys == null ? 0 : keys.size();
			final var toDrop = keys == null
					? java.util.Collections.<byte[]>emptyList()
					: keys.stream().filter(k -> !startsWith(k, KEY_PREFIX)).toList();
			if (!toDrop.isEmpty()) {
				connection.keyCommands().del(toDrop.toArray(byte[][]::new));
			}
			log.info(
					"Redis cache flush on startup: scanned {} entries under '{}', removed {} stale (current prefix '{}')",
					matched, staleRoot, toDrop.size(), KEY_PREFIX);
		} catch (Exception e) {
			log.warn("Could not flush Redis cache on startup: {}", e.getMessage());
		}
	}

	private static boolean startsWith(final byte[] key, final String prefix) {
		final var pBytes = prefix.getBytes();
		if (key.length < pBytes.length) {
			return false;
		}
		for (int i = 0; i < pBytes.length; i++) {
			if (key[i] != pBytes[i]) {
				return false;
			}
		}
		return true;
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
