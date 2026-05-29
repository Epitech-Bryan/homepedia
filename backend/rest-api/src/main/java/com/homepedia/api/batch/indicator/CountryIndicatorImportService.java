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
		WORLD_BANK_INDICATORS.put("SP.POP.GROW",
				new IndicatorSpec("Croissance démographique", "%", IndicatorCategory.POPULATION));
		WORLD_BANK_INDICATORS.put("IT.NET.USER.ZS",
				new IndicatorSpec("Internautes", "% population", IndicatorCategory.INFRASTRUCTURE));
		WORLD_BANK_INDICATORS.put("EN.GHG.CO2.PC.CE.AR5",
				new IndicatorSpec("CO2 par habitant", "tonnes", IndicatorCategory.ENVIRONMENT));
		WORLD_BANK_INDICATORS.put("SH.XPD.CHEX.GD.ZS",
				new IndicatorSpec("Dépenses de santé", "% PIB", IndicatorCategory.HEALTH));
		WORLD_BANK_INDICATORS.put("SE.XPD.TOTL.GD.ZS",
				new IndicatorSpec("Dépenses d'éducation", "% PIB", IndicatorCategory.EDUCATION));
		WORLD_BANK_INDICATORS.put("SI.POV.GINI",
				new IndicatorSpec("Indice de Gini", "0-100", IndicatorCategory.ECONOMY));
	}

	// Eurostat House Price Index (annual, total dwellings, index 2015=100). The
	// only keyless source of real residential-price data beyond France's DVF —
	// covers EU + EFTA + UK + Turkey. Geo codes are ISO-2 (with Eurostat's EL/UK
	// quirks), mapped to the ISO-3 the country layer keys off.
	private static final String EUROSTAT_HPI_URL = "https://ec.europa.eu/eurostat/api/dissemination/sdmx/2.1/data/prc_hpi_a?format=TSV";
	private static final String EUROSTAT_HPI_ROW_PREFIX = "A,TOTAL,I15_A_AVG,";

	private static final Map<String, String> ISO2_TO_ISO3 = Map.ofEntries(Map.entry("AT", "AUT"),
			Map.entry("BE", "BEL"), Map.entry("BG", "BGR"), Map.entry("CH", "CHE"), Map.entry("CY", "CYP"),
			Map.entry("CZ", "CZE"), Map.entry("DE", "DEU"), Map.entry("DK", "DNK"), Map.entry("EE", "EST"),
			Map.entry("EL", "GRC"), Map.entry("ES", "ESP"), Map.entry("FI", "FIN"), Map.entry("FR", "FRA"),
			Map.entry("HR", "HRV"), Map.entry("HU", "HUN"), Map.entry("IE", "IRL"), Map.entry("IS", "ISL"),
			Map.entry("IT", "ITA"), Map.entry("LT", "LTU"), Map.entry("LU", "LUX"), Map.entry("LV", "LVA"),
			Map.entry("MT", "MLT"), Map.entry("NL", "NLD"), Map.entry("NO", "NOR"), Map.entry("PL", "POL"),
			Map.entry("PT", "PRT"), Map.entry("RO", "ROU"), Map.entry("SE", "SWE"), Map.entry("SI", "SVN"),
			Map.entry("SK", "SVK"), Map.entry("TR", "TUR"), Map.entry("UK", "GBR"));

	private static final IndicatorSpec HPI_SPEC = new IndicatorSpec("Indice prix logement", "base 2015=100",
			IndicatorCategory.ECONOMY);

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
		try {
			total += importOecdHousePriceIndex();
		} catch (Exception e) {
			log.warn("OECD house price index failed, skipping: {}", e.getMessage());
		}
		try {
			total += importEurostatHousePriceIndex();
		} catch (Exception e) {
			log.warn("Eurostat house price index failed, skipping: {}", e.getMessage());
		}
		log.info("Country indicator import finished: {} rows across {} World Bank indicators + Eurostat HPI", total,
				WORLD_BANK_INDICATORS.size());
		return total;
	}

	// OECD real house price index (2015=100), SDMX-JSON. Covers the major
	// non-EU economies Eurostat doesn't (US, Japan, Canada, Korea, Australia,
	// Mexico, Brazil, China, India, ...). REF_AREA codes are already ISO-3.
	private static final String OECD_HPI_URL = "https://sdmx.oecd.org/public/rest/data/"
			+ "OECD.ECO.MPD,DSD_AN_HOUSE_PRICES@DF_HOUSE_PRICES,1.0/..RHP.....?startPeriod=2015&dimensionAtObservation=AllDimensions";

	private int importOecdHousePriceIndex() throws IOException, InterruptedException {
		final var request = HttpRequest.newBuilder(URI.create(OECD_HPI_URL)).timeout(HTTP_TIMEOUT)
				.header("Accept", "application/vnd.sdmx.data+json").GET().build();
		final var response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("OECD HPI HTTP " + response.statusCode());
		}
		final var root = objectMapper.readTree(response.body());
		final var dims = root.path("data").path("structures").path(0).path("dimensions").path("observation");
		if (!dims.isArray()) {
			log.warn("OECD HPI response shape unexpected");
			return 0;
		}
		var refDimPos = -1;
		var timeDimPos = -1;
		final List<String> refCodes = new ArrayList<>();
		final List<String> timeCodes = new ArrayList<>();
		for (int d = 0; d < dims.size(); d++) {
			final var id = dims.get(d).path("id").asText("");
			if ("REF_AREA".equals(id)) {
				refDimPos = d;
				dims.get(d).path("values").forEach(v -> refCodes.add(v.path("id").asText("")));
			} else if ("TIME_PERIOD".equals(id)) {
				timeDimPos = d;
				dims.get(d).path("values").forEach(v -> timeCodes.add(v.path("id").asText("")));
			}
		}
		if (refDimPos < 0 || timeDimPos < 0) {
			return 0;
		}

		final var euCovered = new java.util.HashSet<>(ISO2_TO_ISO3.values());
		final var latestYear = new java.util.HashMap<String, Integer>();
		final var latestValue = new java.util.HashMap<String, Double>();
		final var observations = root.path("data").path("dataSets").path(0).path("observations");
		final var fields = observations.fieldNames();
		while (fields.hasNext()) {
			final var key = fields.next();
			final var parts = key.split(":");
			if (parts.length <= Math.max(refDimPos, timeDimPos)) {
				continue;
			}
			final var refIdx = Integer.parseInt(parts[refDimPos]);
			final var timeIdx = Integer.parseInt(parts[timeDimPos]);
			if (refIdx >= refCodes.size() || timeIdx >= timeCodes.size()) {
				continue;
			}
			final var iso3 = refCodes.get(refIdx);
			if (iso3.length() != 3 || euCovered.contains(iso3)) {
				continue;
			}
			final var year = parseYear(timeCodes.get(timeIdx));
			final var valNode = observations.path(key).path(0);
			if (year == null || valNode.isMissingNode() || valNode.isNull()) {
				continue;
			}
			final var prev = latestYear.get(iso3);
			if (prev == null || year > prev) {
				latestYear.put(iso3, year);
				latestValue.put(iso3, valNode.asDouble());
			}
		}

		final List<Object[]> rows = new ArrayList<>(BATCH_SIZE);
		for (final var iso3 : latestValue.keySet()) {
			rows.add(new Object[]{iso3, HPI_SPEC.category().name(), HPI_SPEC.label(), latestValue.get(iso3),
					HPI_SPEC.unit(), latestYear.get(iso3)});
		}
		final var inserted = flush(rows);
		log.info("OECD house price index imported: {} countries", inserted);
		return inserted;
	}

	private int importEurostatHousePriceIndex() throws IOException, InterruptedException {
		final var request = HttpRequest.newBuilder(URI.create(EUROSTAT_HPI_URL)).timeout(HTTP_TIMEOUT)
				.header("Accept", "text/tab-separated-values").GET().build();
		final var response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("Eurostat HPI HTTP " + response.statusCode());
		}
		final var lines = response.body().split("\n");
		if (lines.length < 2) {
			return 0;
		}
		final var years = parseTsvYearColumns(lines[0]);
		final List<Object[]> rows = new ArrayList<>(BATCH_SIZE);
		var inserted = 0;
		for (int li = 1; li < lines.length; li++) {
			final var line = lines[li];
			if (!line.startsWith(EUROSTAT_HPI_ROW_PREFIX)) {
				continue;
			}
			final var fields = line.split("\t");
			final var dims = fields[0].split(",");
			final var iso3 = ISO2_TO_ISO3.get(dims[dims.length - 1].trim());
			if (iso3 == null) {
				continue;
			}
			Double latestValue = null;
			Integer latestYear = null;
			for (int i = 0; i < years.size() && i + 1 < fields.length; i++) {
				final var year = years.get(i);
				final var value = parseTsvValue(fields[i + 1]);
				if (year != null && value != null) {
					latestValue = value;
					latestYear = year;
				}
			}
			if (latestValue != null) {
				rows.add(new Object[]{iso3, HPI_SPEC.category().name(), HPI_SPEC.label(), latestValue, HPI_SPEC.unit(),
						latestYear});
			}
		}
		inserted += flush(rows);
		log.info("Eurostat house price index imported: {} countries", inserted);
		return inserted;
	}

	private static List<Integer> parseTsvYearColumns(final String headerLine) {
		final var fields = headerLine.split("\t");
		final var years = new ArrayList<Integer>();
		for (int i = 1; i < fields.length; i++) {
			years.add(parseYear(fields[i]));
		}
		return years;
	}

	private static Double parseTsvValue(final String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		final var cleaned = raw.replaceAll("[^0-9.\\-]", "").trim();
		if (cleaned.isEmpty()) {
			return null;
		}
		try {
			return Double.parseDouble(cleaned);
		} catch (NumberFormatException e) {
			return null;
		}
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
