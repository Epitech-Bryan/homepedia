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
	 * or legacy entries (France, Norway, Somaliland, Kosovo, N. Cyprus) — those
	 * cases are recovered via {@code ISO_A3_EH} / {@code ADM0_A3} when computing
	 * {@code code}; the raw source columns themselves are not kept.
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

	/**
	 * Belgian municipalities — GADM admin-4 polygons (581 communes) joined with
	 * Wikidata populations (P1567 NIS + P1082) and surfaces (P2046). 98 % of
	 * features carry a population; the rest fall back to "no data" gray on the
	 * choropleth like FR communes without DVF rows.
	 */
	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'belgium-municipalities'")
	public String getBelgiumMunicipalitiesGeoJson() {
		try {
			final var resource = new ClassPathResource("data/belgium-municipalities.geojson");
			try (var in = resource.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Belgium municipalities GeoJSON not available", e);
		}
	}

	private String worldAdmin1Cache;

	/**
	 * Admin-1 boundaries (states/provinces/regions) for ~38 EU + G20 countries,
	 * sourced from GADM 4.1 and Douglas-Peucker-simplified to ~5 km tolerance
	 * (plenty for the zoom 5-7 browse view). 6 MB on disk → ~1.5 MB on the wire
	 * after Brotli, served once and cached forever in the browser.
	 *
	 * <p>
	 * Belgium and France are intentionally excluded from this dataset: Belgium has
	 * its own bundled file with Statbel population; France comes from
	 * geo.api.gouv.fr at higher resolution.
	 *
	 * <p>
	 * Spherical area km² is computed once at boot and baked into every feature so
	 * the frontend choropleth can render an "area" or "density" metric over non-FR
	 * admin-1 regions without yet another payload. Population is not available in
	 * the GADM source — that's what the future Eurostat NUTS 2 importer is for.
	 */
	/**
	 * Admin-2 polygons (counties / districts / Kreise / province) for ~18 European
	 * countries — the level just below admin-1. Total ~6 k features, 3-4 MB on disk
	 * after Douglas-Peucker simplification at 3 km tolerance. Loaded straight from
	 * disk; no enrichment pass since GADM provides name + parent code in the source
	 * already.
	 */
	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'world-admin2'")
	public String getWorldAdmin2GeoJson() {
		try {
			final var resource = new ClassPathResource("data/world-admin2.geojson");
			try (var in = resource.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			throw new IllegalStateException("World admin-2 GeoJSON not available", e);
		}
	}

	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'world-admin1'")
	public String getWorldAdmin1GeoJson() {
		if (worldAdmin1Cache != null) {
			return worldAdmin1Cache;
		}
		try {
			loadAndEnrichWorldAdmin1();
			return worldAdmin1Cache;
		} catch (IOException e) {
			throw new IllegalStateException("World admin-1 GeoJSON not available", e);
		}
	}

	private synchronized void loadAndEnrichWorldAdmin1() throws IOException {
		if (worldAdmin1Cache != null) {
			return;
		}
		final var metricsByCode = loadAdmin1Metrics();
		final var resource = new ClassPathResource("data/world-admin1.geojson");
		try (var in = resource.getInputStream()) {
			final var root = (ObjectNode) objectMapper.readTree(in);
			final var features = (ArrayNode) root.get("features");
			var withPopulation = 0;
			var withGdp = 0;
			for (JsonNode f : features) {
				final var props = (ObjectNode) f.get("properties");
				if (props == null) {
					continue;
				}
				// Computed area from the geometry — kept even when Wikidata
				// publishes a number, because the spherical-cap calc gives
				// a value for every feature whereas P2046 misses ~5 %.
				final var areaKm2 = computeFeatureAreaKm2(f.get("geometry"));
				if (areaKm2 > 0) {
					props.put("area", areaKm2);
				}
				final var code = props.path("code").asText(null);
				if (code == null) {
					continue;
				}
				final var metrics = metricsByCode.get(code);
				if (metrics == null) {
					continue;
				}
				// Wikidata-sourced metrics. Pop covers ~94 % of admin-1
				// entries, gdpNominal ~30 % (sparse but worth surfacing for
				// the regions that do publish it — choropleth grays out the
				// rest, per the design call). Missing fields are simply
				// skipped so the frontend sees no key.
				if (metrics.population != null) {
					props.put("population", metrics.population);
					withPopulation++;
				}
				if (metrics.gdpNominal != null) {
					props.put("gdpNominal", metrics.gdpNominal);
					if (metrics.population != null && metrics.population > 0) {
						props.put("gdpPerCapita", metrics.gdpNominal / metrics.population);
					}
					withGdp++;
				}
			}
			worldAdmin1Cache = objectMapper.writeValueAsString(root);
			log.info("World admin-1 GeoJSON enriched: {} features, {} with pop, {} with gdp", features.size(),
					withPopulation, withGdp);
		}
	}

	/**
	 * Per-feature detail returned by {@link #getWorldAdmin1Detail(String)}. Mirrors
	 * what the world admin-1 region page needs: name + country code + raw metrics +
	 * bbox so the map can fly-to. {@code admin2Count} lets the page render a "12
	 * sub-regions" link without a second roundtrip.
	 */
	public record WorldAdmin1Detail(String code, String name, String country, Long population, Double area,
			Double gdpNominal, Double gdpPerCapita, double[] bbox, int admin2Count) {
	}

	/**
	 * Search result row for {@link #searchWorldAdmin1(String, int)}. The
	 * QuickSearch component renders these as "label / sub" rows alongside FR
	 * regions / departments / communes.
	 */
	public record WorldSearchResult(String code, String name, String country, Long population) {
	}

	/**
	 * Substring search across world admin-1 features. ~3k entries — a linear scan
	 * is fine and beats the complexity of wiring trigram indexes for a non-FR layer
	 * that doesn't have a DB table behind it. Case- and accent-insensitive via
	 * {@link java.text.Normalizer}.
	 */
	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'world-search:' + #query.toLowerCase() + ':' + #limit")
	public List<WorldSearchResult> searchWorldAdmin1(final String query, final int limit) {
		final var folded = fold(query);
		if (folded.isBlank()) {
			return List.of();
		}
		try {
			loadAndEnrichWorldAdmin1();
		} catch (IOException e) {
			return List.of();
		}
		try {
			final var root = (ObjectNode) objectMapper.readTree(worldAdmin1Cache);
			final var features = (ArrayNode) root.get("features");
			final var out = new java.util.ArrayList<WorldSearchResult>();
			for (JsonNode f : features) {
				if (out.size() >= limit * 3) {
					break; // grab a buffer so the pop-sort below has options
				}
				final var props = f.get("properties");
				if (props == null) {
					continue;
				}
				final var name = props.path("name").asText("");
				if (!fold(name).contains(folded)) {
					continue;
				}
				out.add(new WorldSearchResult(props.path("code").asText(null), name, props.path("country").asText(null),
						props.path("population").isMissingNode() ? null : props.get("population").asLong()));
			}
			// Sort by population descending so "Berlin" beats "Berlin (district)"
			// when both match; null pops sink to the bottom.
			out.sort((a, b) -> Long.compare(b.population == null ? 0 : b.population,
					a.population == null ? 0 : a.population));
			return out.stream().limit(limit).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	private static String fold(String s) {
		if (s == null)
			return "";
		final var nfd = java.text.Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT),
				java.text.Normalizer.Form.NFD);
		final var sb = new StringBuilder(nfd.length());
		for (int i = 0; i < nfd.length(); i++) {
			final char c = nfd.charAt(i);
			if (Character.getType(c) != Character.NON_SPACING_MARK) {
				sb.append(c);
			}
		}
		return sb.toString().trim();
	}

	/**
	 * Look up a single admin-1 feature by its GADM GID_1 (e.g. {@code DEU.2_1}).
	 * Returns null if the code is unknown. Hot path: cached behind Redis with the
	 * feature code as key — the world-admin1 GeoJSON is parsed once and the
	 * per-code lookup is a HashMap probe.
	 */
	@Cacheable(value = CacheConfig.CACHE_GEO, key = "'world-admin1-detail:' + #code", unless = "#result == null")
	public WorldAdmin1Detail getWorldAdmin1Detail(final String code) {
		try {
			loadAndEnrichWorldAdmin1();
		} catch (IOException e) {
			throw new IllegalStateException("World admin-1 GeoJSON not available", e);
		}
		try {
			final var root = (ObjectNode) objectMapper.readTree(worldAdmin1Cache);
			final var features = (ArrayNode) root.get("features");
			for (JsonNode f : features) {
				final var props = f.get("properties");
				if (props == null) {
					continue;
				}
				if (!code.equals(props.path("code").asText(null))) {
					continue;
				}
				final var bbox = computeBbox(f.get("geometry"));
				final var country = props.path("country").asText(null);
				final var admin2Count = countAdmin2Children(country, code);
				return new WorldAdmin1Detail(code, props.path("name").asText(null), country,
						props.path("population").isMissingNode() ? null : props.get("population").asLong(),
						props.path("area").isMissingNode() ? null : props.get("area").asDouble(),
						props.path("gdpNominal").isMissingNode() ? null : props.get("gdpNominal").asDouble(),
						props.path("gdpPerCapita").isMissingNode() ? null : props.get("gdpPerCapita").asDouble(), bbox,
						admin2Count);
			}
			return null;
		} catch (IOException e) {
			throw new IllegalStateException("Failed to parse world admin-1", e);
		}
	}

	private int countAdmin2Children(String countryCode, String admin1Code) {
		if (countryCode == null) {
			return 0;
		}
		try {
			final var resource = new ClassPathResource("data/world-admin2.geojson");
			if (!resource.exists()) {
				return 0;
			}
			try (var in = resource.getInputStream()) {
				final var root = (ObjectNode) objectMapper.readTree(in);
				final var features = (ArrayNode) root.get("features");
				var count = 0;
				for (JsonNode f : features) {
					final var props = f.get("properties");
					if (props == null) {
						continue;
					}
					if (admin1Code.equals(props.path("parent_code").asText(null))) {
						count++;
					}
				}
				return count;
			}
		} catch (IOException e) {
			return 0;
		}
	}

	private double[] computeBbox(JsonNode geometry) {
		final var bbox = new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
				Double.NEGATIVE_INFINITY};
		walkCoordinates(geometry.get("coordinates"), bbox);
		return bbox;
	}

	private void walkCoordinates(JsonNode coords, double[] bbox) {
		if (coords == null || !coords.isArray()) {
			return;
		}
		if (coords.size() > 0 && coords.get(0).isNumber()) {
			final var x = coords.get(0).asDouble();
			final var y = coords.get(1).asDouble();
			if (x < bbox[0])
				bbox[0] = x;
			if (y < bbox[1])
				bbox[1] = y;
			if (x > bbox[2])
				bbox[2] = x;
			if (y > bbox[3])
				bbox[3] = y;
			return;
		}
		for (JsonNode child : coords) {
			walkCoordinates(child, bbox);
		}
	}

	private record Admin1Metrics(Long population, Double area, Double gdpNominal) {
	}

	private java.util.Map<String, Admin1Metrics> loadAdmin1Metrics() {
		final var resource = new ClassPathResource("data/world-admin1-metrics.json");
		if (!resource.exists()) {
			log.warn("world-admin1-metrics.json missing — admin-1 choropleth will fall back to area only");
			return java.util.Map.of();
		}
		try (var in = resource.getInputStream()) {
			final var node = (ObjectNode) objectMapper.readTree(in);
			final var out = new java.util.HashMap<String, Admin1Metrics>();
			node.fields().forEachRemaining(e -> {
				final var v = e.getValue();
				final var pop = v.path("population");
				final var area = v.path("area");
				final var gdp = v.path("gdpNominal");
				out.put(e.getKey(),
						new Admin1Metrics(pop.isMissingNode() || pop.isNull() ? null : pop.asLong(),
								area.isMissingNode() || area.isNull() ? null : area.asDouble(),
								gdp.isMissingNode() || gdp.isNull() ? null : gdp.asDouble()));
			});
			return out;
		} catch (IOException e) {
			log.error("Failed to parse world-admin1-metrics.json: {}", e.getMessage());
			return java.util.Map.of();
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

				// Read the candidate code columns BEFORE stripping. Natural
				// Earth flags France/Norway as ISO_A3=-99 (legacy diplomatic
				// reasons) and Somaliland/Kosovo/N. Cyprus the same way for
				// non-recognition. ISO_A3_EH (Even Hansen) recovers
				// FRA/NOR; ADM0_A3 carries the de-facto code for the
				// remaining disputed territories (SOL, KOS, CYN). Without
				// this fallback those countries had no `code` at all and
				// dropped out of the choropleth join entirely.
				final var isoA3 = props.path("ISO_A3").asText(null);
				final var isoEh = props.path("ISO_A3_EH").asText(null);
				final var admA3 = props.path("ADM0_A3").asText(null);

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

				final var name = props.path("NAME").asText(null);
				final var pop = props.path("POP_EST");
				final var gdp = props.path("GDP_MD");

				String code = null;
				if (isoA3 != null && !"-99".equals(isoA3)) {
					code = isoA3;
				} else if (isoEh != null && !"-99".equals(isoEh)) {
					code = isoEh;
				} else if (admA3 != null && !"-99".equals(admA3)) {
					code = admA3;
				}
				if (code != null) {
					props.put("code", code);
				}
				if (name != null) {
					props.put("name", name);
				}
				if (!pop.isMissingNode() && !pop.isNull()) {
					props.set("population", pop);
				}
				// Natural Earth ships GDP in millions USD (2019 vintage); pass
				// it through as `gdp` so the frontend can drive a GDP-per-capita
				// choropleth at world zoom without another payload.
				if (!gdp.isMissingNode() && !gdp.isNull()) {
					props.set("gdp", gdp);
				}

				// Compute a spherical-area km² for every feature so the world
				// view can drive density (population / area) — Natural Earth
				// itself ships no area column. Done once at boot, baked into
				// the cached payload.
				final var areaKm2 = computeFeatureAreaKm2(f.get("geometry"));
				if (areaKm2 > 0) {
					props.put("area", areaKm2);
				}
			}
			trimmedGeoJsonCache = objectMapper.writeValueAsString(root);
		}
		log.info("Country GeoJSON trimmed: {} features, {} bytes",
				((ArrayNode) objectMapper.readTree(trimmedGeoJsonCache).get("features")).size(),
				trimmedGeoJsonCache.getBytes(StandardCharsets.UTF_8).length);
	}

	/**
	 * Mean Earth radius (IUGG R1) in metres. Picked over the equatorial radius
	 * because we need a single-value approximation for spherical area; R1 minimises
	 * the average error against the WGS-84 ellipsoid.
	 */
	private static final double EARTH_RADIUS_M = 6_371_008.8;

	private static double computeFeatureAreaKm2(JsonNode geometry) {
		if (geometry == null || geometry.isNull()) {
			return 0.0;
		}
		final var type = geometry.path("type").asText("");
		final var coords = geometry.path("coordinates");
		double areaM2 = 0.0;
		if ("Polygon".equals(type)) {
			areaM2 = polygonAreaM2(coords);
		} else if ("MultiPolygon".equals(type)) {
			for (JsonNode poly : coords) {
				areaM2 += polygonAreaM2(poly);
			}
		}
		return areaM2 / 1_000_000.0;
	}

	/**
	 * Spherical polygon area on a sphere of radius {@link #EARTH_RADIUS_M}. Uses
	 * Green's theorem on the sphere — accurate to within a few percent of the
	 * WGS-84 ellipsoid result for country-sized shapes. Outer ring counts positive,
	 * inner rings (holes) subtract.
	 */
	private static double polygonAreaM2(JsonNode polygonCoords) {
		if (polygonCoords == null || !polygonCoords.isArray() || polygonCoords.isEmpty()) {
			return 0.0;
		}
		double area = Math.abs(ringAreaM2(polygonCoords.get(0)));
		for (int i = 1; i < polygonCoords.size(); i++) {
			area -= Math.abs(ringAreaM2(polygonCoords.get(i)));
		}
		return Math.max(area, 0.0);
	}

	private static double ringAreaM2(JsonNode ring) {
		if (ring == null || !ring.isArray() || ring.size() < 3) {
			return 0.0;
		}
		double total = 0.0;
		final int n = ring.size();
		for (int i = 0; i < n; i++) {
			final var p1 = ring.get(i);
			final var p2 = ring.get((i + 1) % n);
			final double lon1 = Math.toRadians(p1.get(0).asDouble());
			final double lat1 = Math.toRadians(p1.get(1).asDouble());
			final double lon2 = Math.toRadians(p2.get(0).asDouble());
			final double lat2 = Math.toRadians(p2.get(1).asDouble());
			total += (lon2 - lon1) * (2.0 + Math.sin(lat1) + Math.sin(lat2));
		}
		return total * EARTH_RADIUS_M * EARTH_RADIUS_M / 2.0;
	}
}
