package com.homepedia.api.batch.indicator;

import com.homepedia.common.indicator.IndicatorCategory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * Pulls Eurostat indicators at NUTS 2 granularity (issue #7 phase 1) so the map
 * can colour foreign-EU polygons the same way it colours French ones. Hits the
 * Eurostat SDMX REST endpoint that returns TSV — no API key required, generous
 * rate limits, every dataset addressable by its code:
 * 
 * <pre>
 * https://ec.europa.eu/eurostat/api/dissemination/sdmx/2.1/data/&lt;dataset&gt;?format=TSV
 * </pre>
 *
 * <p>
 * Stored as
 * {@code (geographic_level='NUTS2', geographic_code=&lt;NUTS code&gt;,
 * category, label, indicator_value, unit, year)} rows in the existing
 * {@code indicators} table — the schema already widened to VARCHAR(9)
 * (changeset 014) accepts the 4-char NUTS 2 codes.
 *
 * <p>
 * Curated dataset set — every entry in {@link #EUROSTAT_DATASETS} below becomes
 * a fresh import on the next job run. The Eurostat TSV layout encodes the
 * dimension labels in the first column ("freq,unit,age,sex,geo") — we only care
 * about (geo, year, value) so we project hard and ignore everything else.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EurostatImportService {

	private static final int BATCH_SIZE = 1000;
	private static final String SDMX_BASE = "https://ec.europa.eu/eurostat/api/dissemination/sdmx/2.1/data/";
	private static final Duration HTTP_TIMEOUT = Duration.ofMinutes(5);

	private static final Map<String, DatasetSpec> EUROSTAT_DATASETS = new LinkedHashMap<>();

	static {
		EUROSTAT_DATASETS.put("nama_10r_2gdp",
				new DatasetSpec("PIB par habitant", "EUR/habitant", IndicatorCategory.ECONOMY));
		EUROSTAT_DATASETS.put("lfst_r_lfu3rt", new DatasetSpec("Taux de chômage", "%", IndicatorCategory.ECONOMY));
		EUROSTAT_DATASETS.put("demo_r_pjangrp3", new DatasetSpec("Population", "habitants", IndicatorCategory.ECONOMY));
	}

	private static final String INSERT_SQL = """
			INSERT INTO indicators (geographic_level, geographic_code, category, label, indicator_value, unit, year)
			VALUES ('NUTS2', ?, ?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbcTemplate;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

	public int importAllDatasets() {
		var total = 0;
		for (var entry : EUROSTAT_DATASETS.entrySet()) {
			try {
				total += importDataset(entry.getKey(), entry.getValue());
			} catch (Exception e) {
				// One dataset failing shouldn't kill the rest of the import —
				// Eurostat occasionally times out on a single endpoint while
				// the others serve fine. Log and continue.
				log.warn("Eurostat dataset {} failed, skipping: {}", entry.getKey(), e.getMessage());
			}
		}
		return total;
	}

	public int importDataset(final String datasetCode, final DatasetSpec spec)
			throws IOException, InterruptedException {
		final var url = SDMX_BASE + datasetCode + "?format=TSV";
		log.info("Eurostat fetch starting: {} ({})", datasetCode, spec.label());

		final var request = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT)
				.header("Accept", "text/tab-separated-values").GET().build();
		final var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			throw new IOException("Eurostat HTTP " + response.statusCode() + " for " + datasetCode);
		}

		final List<Object[]> rows = new ArrayList<>(BATCH_SIZE);
		var inserted = 0;

		try (final var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
			final var headerLine = reader.readLine();
			if (headerLine == null) {
				log.warn("Eurostat response empty for {}", datasetCode);
				return 0;
			}
			final var years = parseYearColumns(headerLine);

			String line;
			while ((line = reader.readLine()) != null) {
				inserted += parseRow(line, years, spec, rows);
				if (rows.size() >= BATCH_SIZE) {
					flush(rows);
				}
			}
			flush(rows);
		}
		log.info("Eurostat dataset {} imported: {} rows", datasetCode, inserted);
		return inserted;
	}

	// Eurostat TSV header looks like:
	// freq,unit,age,sex,geo\\TIME_PERIOD 2024 2023 2022 …
	// The first cell is the dimension key ("\\TIME_PERIOD" is literal), the rest
	// are reverse-chronological years.
	private static List<Integer> parseYearColumns(final String headerLine) {
		final var fields = headerLine.split("\t");
		final var years = new ArrayList<Integer>();
		for (int i = 1; i < fields.length; i++) {
			try {
				years.add(Integer.parseInt(fields[i].trim()));
			} catch (NumberFormatException e) {
				years.add(null);
			}
		}
		return years;
	}

	private int parseRow(final String line, final List<Integer> years, final DatasetSpec spec,
			final List<Object[]> rows) {
		final var fields = line.split("\t");
		if (fields.length < 2) {
			return 0;
		}
		// First cell holds the comma-separated dimension values; the NUTS code
		// is always the last one (Eurostat convention).
		final var dimensions = fields[0].split(",");
		final var geoCode = dimensions[dimensions.length - 1].trim();
		// We want NUTS 2: 4 chars (e.g. "FR10", "DE11"). NUTS 0/1/3 also show
		// up in the same TSV — skip the others.
		if (geoCode.length() != 4) {
			return 0;
		}

		var added = 0;
		for (int i = 0; i < years.size() && i + 1 < fields.length; i++) {
			final var year = years.get(i);
			if (year == null) {
				continue;
			}
			final var value = parseValue(fields[i + 1]);
			if (value == null) {
				continue;
			}
			rows.add(new Object[]{geoCode, spec.category().name(), spec.label(), value, spec.unit(), year});
			added++;
		}
		return added;
	}

	// Eurostat marks missing data with ":" (and occasionally with flag letters
	// like "b", "e", "p" appended). Strip the flag, treat colon as NULL.
	private static Double parseValue(final String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		final var cleaned = raw.replaceAll("[a-zA-Z]", "").trim();
		if (cleaned.isEmpty() || ":".equals(cleaned)) {
			return null;
		}
		try {
			return Double.parseDouble(cleaned);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void flush(final List<Object[]> rows) {
		if (rows.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(INSERT_SQL, rows);
		rows.clear();
	}

	public record DatasetSpec(String label, String unit, IndicatorCategory category) {
	}
}
