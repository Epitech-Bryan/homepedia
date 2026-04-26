package com.homepedia.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
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
 * Cache names appear in {@code @Cacheable(value = "...")} on services. Cache
 * keys are generated from method arguments by Spring's default key generator.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	public static final String CACHE_GEO = "geo";
	public static final String CACHE_REFDATA = "refdata";
	public static final String CACHE_STATS = "stats";
	public static final String CACHE_REVIEWS = "reviews";

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		final var jsonMapper = objectMapper.copy().activateDefaultTyping(
				BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
				ObjectMapper.DefaultTyping.NON_FINAL);
		final var jsonSerializer = new GenericJackson2JsonRedisSerializer(jsonMapper);

		final var defaults = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(1))
				.serializeKeysWith(
						RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
				.disableCachingNullValues();

		final var perCache = Map.of(CACHE_GEO, defaults.entryTtl(Duration.ofHours(24)), CACHE_REFDATA,
				defaults.entryTtl(Duration.ofHours(12)), CACHE_STATS, defaults.entryTtl(Duration.ofMinutes(30)),
				CACHE_REVIEWS, defaults.entryTtl(Duration.ofMinutes(15)));

		return RedisCacheManager.builder(connectionFactory).cacheDefaults(defaults)
				.withInitialCacheConfigurations(perCache).transactionAware().build();
	}
}
