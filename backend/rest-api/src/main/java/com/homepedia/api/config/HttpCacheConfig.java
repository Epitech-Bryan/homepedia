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
 * Browser/proxy cache for read-only endpoints. Two tiers:
 *
 * <ul>
 * <li><b>Reference data</b> ({@code /geo}, {@code /regions},
 * {@code /departments}) — INSEE/IGN data that changes at most once a year, on a
 * manual import. 24h max-age so the webapp's repeated lookups (every page
 * render fetches the region list, department dropdowns, GeoJSON boundaries) hit
 * the browser cache instead of the server.</li>
 * <li><b>Stats</b> ({@code /stats}) — refreshed after every DVF partition swap
 * (~daily during active imports). 5 min max-age + ETag, same as before.</li>
 * </ul>
 *
 * Both tiers get a {@link ShallowEtagHeaderFilter} so revalidation
 * short-circuits with {@code 304 Not Modified} when the body hasn't changed
 * (cheap for the reference data, useful for stats during the 5-min window).
 * {@code public} so Traefik can shared-cache; {@code stale-while-revalidate}
 * lets the browser keep serving the old payload for a few seconds while it
 * refetches.
 *
 * Server-side Redis cache still owns the heavy lifting; the HTTP layer is a
 * thinner cache on top.
 */
@Configuration
public class HttpCacheConfig {

	private static final String[] REFDATA_PATHS = {"/geo/*", "/regions", "/regions/*", "/departments",
			"/departments/*"};
	private static final String[] STATS_PATHS = {"/stats/*"};
	// Viewport-driven map endpoints: same cache window as the server-side
	// Redis cache (60 s for heatpoints / markers). Short max-age means a
	// pan-and-come-back replays from the browser cache, but a stat refresh
	// after an import shows up on the next request anyway.
	private static final String[] VIEWPORT_PATHS = {"/transactions/heatpoints", "/transactions/markers"};

	private static final String REFDATA_CACHE_CONTROL = "public, max-age=86400, stale-while-revalidate=600";
	private static final String STATS_CACHE_CONTROL = "public, max-age=300, stale-while-revalidate=60";
	private static final String VIEWPORT_CACHE_CONTROL = "public, max-age=60, stale-while-revalidate=30";

	@Bean
	public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
		final var bean = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		bean.addUrlPatterns(merge(merge(REFDATA_PATHS, STATS_PATHS), VIEWPORT_PATHS));
		bean.setName("shallowEtagFilter");
		return bean;
	}

	@Bean
	public FilterRegistrationBean<OncePerRequestFilter> refdataCacheControlFilter() {
		return cacheControlFilter("refdataCacheControlFilter", REFDATA_CACHE_CONTROL, REFDATA_PATHS);
	}

	@Bean
	public FilterRegistrationBean<OncePerRequestFilter> statsCacheControlFilter() {
		return cacheControlFilter("statsCacheControlFilter", STATS_CACHE_CONTROL, STATS_PATHS);
	}

	@Bean
	public FilterRegistrationBean<OncePerRequestFilter> viewportCacheControlFilter() {
		return cacheControlFilter("viewportCacheControlFilter", VIEWPORT_CACHE_CONTROL, VIEWPORT_PATHS);
	}

	private FilterRegistrationBean<OncePerRequestFilter> cacheControlFilter(final String name, final String header,
			final String[] paths) {
		final OncePerRequestFilter filter = new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
					throws ServletException, IOException {
				if (!res.containsHeader("Cache-Control")) {
					res.setHeader("Cache-Control", header);
				}
				chain.doFilter(req, res);
			}
		};
		final var bean = new FilterRegistrationBean<>(filter);
		bean.addUrlPatterns(paths);
		bean.setName(name);
		return bean;
	}

	private static String[] merge(final String[] a, final String[] b) {
		final var out = new String[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}
}
