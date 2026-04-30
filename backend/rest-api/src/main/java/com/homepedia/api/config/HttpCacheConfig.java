package com.homepedia.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Browser/proxy cache for stats and geo responses, which are read-only and
 * change at most once per import (DVF/INSEE). Two filters chained:
 *
 * <ol>
 * <li>{@link ShallowEtagHeaderFilter} — computes a strong ETag (MD5 of the
 * response body) and short-circuits with {@code 304 Not Modified} when the
 * client sends a matching {@code If-None-Match}. Avoids re-sending the payload
 * at all on browser-cache revalidation.</li>
 * <li>{@code Cache-Control: public, max-age=300} — lets browsers and any
 * downstream proxy (Traefik) serve from cache for 5 min without a round-trip.
 * {@code public} so shared caches can store it; the data is not
 * user-specific.</li>
 * </ol>
 *
 * Server-side Redis cache (30 min TTL) still owns refreshing on import — the
 * browser cache is a thinner layer on top.
 */
@Configuration
public class HttpCacheConfig {

	private static final String[] CACHEABLE_PATHS = {"/stats/*", "/geo/*"};
	private static final String CACHE_CONTROL = "public, max-age=300, stale-while-revalidate=60";

	@Bean
	public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
		final var bean = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		bean.addUrlPatterns(CACHEABLE_PATHS);
		bean.setName("shallowEtagFilter");
		return bean;
	}

	@Bean
	public FilterRegistrationBean<OncePerRequestFilter> cacheControlFilter() {
		final OncePerRequestFilter filter = new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
					throws ServletException, IOException {
				if (!res.containsHeader("Cache-Control")) {
					res.setHeader("Cache-Control", CACHE_CONTROL);
				}
				chain.doFilter(req, res);
			}
		};
		final var bean = new FilterRegistrationBean<>(filter);
		bean.addUrlPatterns(CACHEABLE_PATHS);
		bean.setName("cacheControlFilter");
		return bean;
	}
}
