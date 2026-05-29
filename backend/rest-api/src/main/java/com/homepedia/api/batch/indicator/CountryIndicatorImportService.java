package com.homepedia.api.batch.indicator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homepedia.common.indicator.IndicatorCategory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Pulls country-level indicators (population, GDP, GDP per capita) for every
 * economy on Earth from the World Bank Open Data API and stores them at
 * {@code geographic_level='COUNTRY'} in the {@code indicators} table, keyed by
 * ISO 3166-1 alpha-3 (the World Bank {@code countryiso3code} field matches the
 * {@code code} our country GeoJSON / MVT layer uses). This gives the world view
 * current, complete metrics instead of Natural Earth's static 2019 snapshot.
 *
 * <p>
 * No API key, generous rate limits. {@code mrnev=1} returns the most recent
 * non-empty value per country in a single page, so one HTTP round-trip per
 * indicator covers ~260 economies — the whole import is three calls.
 *
 * <pre>
 * https://api.worldbank.org/v2/country/all/indicator/SP.POP.TOTL?format=json&amp;per_page=400&amp;mrnev=1
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountryIndicatorImportService {

	private static final String WB_BASE = "https://api.worldbank.org/v2/country/all/indicator/";
	private static final Duration HTTP_TIMEOUT = Duration.ofMinutes(2);
	private static final int BATCH_SIZE = 500;

	private static final Map<String, IndicatorSpec> WORLD_BANK_INDICATORS = new LinkedHashMap<>();

	static {
		WORLD_BANK_INDICATORS.put("SP.POP.TOTL",
				new IndicatorSpec("Population", "habitants", IndicatorCategory.POPULATION));
		WORLD_BANK_INDICATORS.put("NY.GDP.MKTP.CD", new IndicatorSpec("PIB", "USD", IndicatorCategory.ECONOMY));
		WORLD_BANK_INDICATORS.put("NY.GDP.PCAP.CD",
				new IndicatorSpec("PIB par habitant", "USD/habitant", IndicatorCategory.ECONOMY));
		WORLD_BANK_INDICATORS.put("NY.GDP.MKTP.KD.ZG",
				new IndicatorSpec("Croissance du PIB", "%", IndicatorCategory.ECONOMY));
		WORLD_BANK_INDICATORS.put("FP.CPI.TOTL.ZG", new IndicatorSpec("Inflation", "%", IndicatorCategory.ECONOMY));
		WORLD_BANK_INDICATORS.put("SL.UEM.TOTL.ZS",
				new IndicatorSpec("Taux de chômage", "%", IndicatorCategory.ECONOMY));
		WORLD_BANK_INDICATORS.put("SP.DYN.LE00.IN",
				new IndicatorSpec("Espérance de vie", "années", IndicatorCategory.HEALTH));
		WORLD_BANK_INDICATORS.put("SP.URB.TOTL.IN.ZS",
				new IndicatorSpec("Population urbaine", "%", IndicatorCategory.POPULATION));
		WORLD_BANK_INDICATORS.put("EN.POP.DNST",
				new IndicatorSpec("Densité de population", "hab/km²", IndicatorCategory.POPULATION));
	}

	private static final String DELETE_SQL = "DELETE FROM indicators WHERE geographic_level = 'COUNTRY'";
	private static final String INSERT_SQL = """
			INSERT INTO indicators (geographic_level, geographic_code, category, label, indicator_value, unit, year)
			VALUES ('COUNTRY', ?, ?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

	public int importAll() {
		jdbcTemplate.update(DELETE_SQL);
		var total = 0;
		for (final var entry : WORLD_BANK_INDICATORS.entrySet()) {
			try {
				total += importIndicator(entry.getKey(), entry.getValue());
			} catch (Exception e) {
				log.warn("World Bank indicator {} failed, skipping: {}", entry.getKey(), e.getMessage());
			}
		}
		log.info("Country indicator import finished: {} rows across {} indicators", total,
				WORLD_BANK_INDICATORS.size());
		return total;
	}

	private int importIndicator(final String indicatorCode, final IndicatorSpec spec)
			throws IOException, InterruptedException {
		// A date range (last ~12 years) rather than mrnev=1: the most-recent-
		// non-empty-value mode returns HTTP 400 for some indicators (CPI,
		// unemployment, ...) when the recent window is sparse. The range is
		// reliable for every indicator; we keep the latest year with a value
		// per country below.
		final var url = WB_BASE + indicatorCode + "?format=json&per_page=20000&date=2013:2025";
		final var request = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT)
				.header("Accept", "application/json").GET().build();
		final var response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("World Bank HTTP " + response.statusCode() + " for " + indicatorCode);
		}
		final var root = objectMapper.readTree(response.body());
		if (!root.isArray() || root.size() < 2 || !root.get(1).isArray()) {
			log.warn("World Bank response shape unexpected for {}", indicatorCode);
			return 0;
		}

		final var latestYear = new java.util.HashMap<String, Integer>();
		final var latestValue = new java.util.HashMap<String, Double>();
		for (final JsonNode row : root.get(1)) {
			final var iso3 = row.path("countryiso3code").asText("");
			final var valueNode = row.path("value");
			if (iso3.length() != 3 || valueNode.isMissingNode() || valueNode.isNull()) {
				continue;
			}
			final var year = parseYear(row.path("date").asText(null));
			if (year == null) {
				continue;
			}
			final var prev = latestYear.get(iso3);
			if (prev == null || year > prev) {
				latestYear.put(iso3, year);
				latestValue.put(iso3, valueNode.asDouble());
			}
		}

		final List<Object[]> rows = new ArrayList<>(BATCH_SIZE);
		var inserted = 0;
		for (final var iso3 : latestValue.keySet()) {
			rows.add(new Object[]{iso3, spec.category().name(), spec.label(), latestValue.get(iso3), spec.unit(),
					latestYear.get(iso3)});
			if (rows.size() >= BATCH_SIZE) {
				inserted += flush(rows);
			}
		}
		inserted += flush(rows);
		log.info("World Bank indicator {} ({}) imported: {} countries", indicatorCode, spec.label(), inserted);
		return inserted;
	}

	private static Integer parseYear(final String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private int flush(final List<Object[]> rows) {
		if (rows.isEmpty()) {
			return 0;
		}
		final var n = rows.size();
		jdbcTemplate.batchUpdate(INSERT_SQL, rows);
		rows.clear();
		return n;
	}

	public record IndicatorSpec(String label, String unit, IndicatorCategory category) {
	}
}
