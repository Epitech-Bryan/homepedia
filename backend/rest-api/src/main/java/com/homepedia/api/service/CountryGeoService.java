package com.homepedia.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.homepedia.api.config.CacheConfig;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Serves the Natural Earth Admin 0 countries dataset (110m resolution, public
 * domain, ~820 KB on disk). The map renders this layer at world-level zoom
 * before falling back to French regions/departments at higher zooms.
 *
 * <p>
 * The raw Natural Earth GeoJSON ships ~80 properties per feature (translated
 * names, statistical estimates, OECD codes, ...). We strip them down to the
 * handful actually used by the frontend: the ISO 3-letter code, display name,
 * population estimate, GDP, continent, region. This drops the response from
 * ~820 KB to ~120 KB before compression — a single small payload that's cached
 * aggressively (Redis 24 h, browser 5 min via Cache-Control).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountryGeoService {

	/**
	 * Properties to keep in the trimmed FeatureCollection. Anything outside this
	 * allow-list is dropped at parse time. ISO_A3 may be {@code -99} for disputed
	 * territories — we keep them but the frontend filters those out for joins
	 * against external datasets.
	 */
	private static final List<String> KEPT_PROPS = List.of("ISO_A3", "ISO_A2", "NAME", "NAME_LONG", "POP_EST", "GDP_MD",
			"CONTINENT", "REGION_UN", "SUBREGION");

	private final ObjectMapper objectMapper;

	private String trimmedGeoJsonCache;

	@PostConstruct
	public void warmUp() {
		try {
			loadAndTrim();
			log.info("Country GeoJSON loaded: {} bytes after property trim", trimmedGeoJsonCache.length());
		} catch (IOException e) {
			log.error("Failed to load Natural Earth countries GeoJSON", e);
		}
	}

	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'countries'")
	public String getCountriesGeoJson() {
		if (trimmedGeoJsonCache == null) {
			try {
				loadAndTrim();
			} catch (IOException e) {
				throw new IllegalStateException("Country GeoJSON not available", e);
			}
		}
		return trimmedGeoJsonCache;
	}

	/**
	 * Belgium provinces (10 provinces + Brussels-Capital region) — boundaries from
	 * GADM 4.1, population/area baked in from Statbel 2024 estimates. Browse-only:
	 * the metric layer can colour them by population/density, but no DVF/DPE for
	 * Belgium yet.
	 */
	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'belgium-provinces'")
	public String getBelgiumProvincesGeoJson() {
		try {
			final var resource = new ClassPathResource("data/belgium-provinces.geojson");
			try (var in = resource.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Belgium provinces GeoJSON not available", e);
		}
	}

	private synchronized void loadAndTrim() throws IOException {
		if (trimmedGeoJsonCache != null) {
			return;
		}
		final var resource = new ClassPathResource("data/countries.geojson");
		try (var in = resource.getInputStream()) {
			final var root = (ObjectNode) objectMapper.readTree(in);
			final var features = (ArrayNode) root.get("features");
			for (JsonNode f : features) {
				final var props = (ObjectNode) f.get("properties");
				// Strip everything outside the kept allow-list, then alias the
				// Natural Earth field names to the conventions the rest of the
				// app uses: code/name/population. The map's choropleth +
				// hover/click handlers all key off `code` and `name`, so this
				// keeps the country layer interchangeable with the
				// region/department/city layers without frontend special-casing.
				final var fieldNames = props.fieldNames();
				final var toRemove = new java.util.ArrayList<String>();
				while (fieldNames.hasNext()) {
					final var name = fieldNames.next();
					if (!KEPT_PROPS.contains(name)) {
						toRemove.add(name);
					}
				}
				toRemove.forEach(props::remove);

				final var iso = props.path("ISO_A3").asText(null);
				final var name = props.path("NAME").asText(null);
				final var pop = props.path("POP_EST");
				if (iso != null && !"-99".equals(iso)) {
					props.put("code", iso);
				}
				if (name != null) {
					props.put("name", name);
				}
				if (!pop.isMissingNode() && !pop.isNull()) {
					props.set("population", pop);
				}
			}
			trimmedGeoJsonCache = objectMapper.writeValueAsString(root);
		}
		log.info("Country GeoJSON trimmed: {} features, {} bytes",
				((ArrayNode) objectMapper.readTree(trimmedGeoJsonCache).get("features")).size(),
				trimmedGeoJsonCache.getBytes(StandardCharsets.UTF_8).length);
	}
}
