package com.homepedia.api.batch.indicator;

import com.homepedia.common.indicator.IndicatorCategory;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Imports INSEE Filosofi indicators at IRIS granularity (issue #10). Filosofi
 * ships a wide CSV (one row per IRIS, columns per indicator) — the source
 * bundle is {@code BASE_TD_FILO_DISP_IRIS_<year>.csv}, ~50k rows × ~60 columns.
 * This service pivots that wide format into the long {@code indicators} table
 * the rest of the app already uses, so frontend queries hitting
 * {@code /api/cities/{insee}/iris-indicators} (endpoint already live,
 * foundations changeset 014/016) don't need a separate code-path.
 *
 * <p>
 * Only a curated subset of Filosofi columns is imported — the full bundle has
 * deciles, age buckets, household-type splits, etc. that aren't surfaced in the
 * UI today. The {@link #FILOSOFI_COLUMNS} map below is the contract: add a
 * column there + a label on the frontend selector to surface a new metric.
 *
 * <p>
 * The downloader (pulling
 * {@code https://www.insee.fr/fr/statistiques/fichier/<id>/BASE_TD_FILO_DISP_IRIS_<year>_CSV.zip})
 * is deliberately not wired here: the file is ~50 MB zipped, releases happen
 * roughly yearly, and we'd rather have an admin trigger pulling the file
 * out-of-band than a job that fails silently when INSEE rotates the URL. The
 * companion job config (when wired) will read
 * {@code homepedia.filosofi.csv-path} the same way
 * {@link EconomyImportJobConfig} reads its CSV.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InseeFilosofiImportService {

	private static final int BATCH_SIZE = 1000;
	private static final String IRIS_CODE_COLUMN = "IRIS";

	// Filosofi column → indicator label/unit pair. Add a row here to surface a
	// new metric — the IRIS_INDICATORS endpoint and the frontend overlay will
	// pick it up on the next import without code changes elsewhere.
	private static final Map<String, IndicatorSpec> FILOSOFI_COLUMNS = new LinkedHashMap<>();

	static {
		FILOSOFI_COLUMNS.put("DISP_MED", new IndicatorSpec("Niveau de vie médian", "€", IndicatorCategory.ECONOMY));
		FILOSOFI_COLUMNS.put("DISP_TP60",
				new IndicatorSpec("Taux de pauvreté (seuil 60%)", "%", IndicatorCategory.ECONOMY));
		FILOSOFI_COLUMNS.put("DISP_GI", new IndicatorSpec("Indice de Gini", "ratio", IndicatorCategory.ECONOMY));
		FILOSOFI_COLUMNS.put("DISP_D1",
				new IndicatorSpec("1er décile de niveau de vie", "€", IndicatorCategory.ECONOMY));
		FILOSOFI_COLUMNS.put("DISP_D9",
				new IndicatorSpec("9ème décile de niveau de vie", "€", IndicatorCategory.ECONOMY));
	}

	private static final String INSERT_SQL = """
			INSERT INTO indicators (geographic_level, geographic_code, category, label, indicator_value, unit, year)
			VALUES ('IRIS', ?, ?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbcTemplate;

	public int importFilosofiIris(final Path csvPath, final int year) throws IOException {
		log.info("Filosofi IRIS import starting from {} (reference year {})", csvPath, year);
		final var rows = new ArrayList<Object[]>(BATCH_SIZE);
		var inserted = 0;
		var skipped = 0;

		try (final var reader = new BufferedReader(Files.newBufferedReader(csvPath, StandardCharsets.UTF_8))) {
			final var headerLine = reader.readLine();
			if (headerLine == null) {
				log.warn("Filosofi CSV is empty at {}", csvPath);
				return 0;
			}
			final var columnIndex = indexHeader(headerLine);
			final var irisIdx = columnIndex.get(IRIS_CODE_COLUMN);
			if (irisIdx == null) {
				throw new IllegalStateException(
						"Filosofi CSV missing IRIS column — refusing to import. Header: " + headerLine);
			}

			String line;
			while ((line = reader.readLine()) != null) {
				final var fields = splitFilosofi(line);
				if (fields.length <= irisIdx) {
					skipped++;
					continue;
				}
				final var irisCode = fields[irisIdx].trim();
				// IRIS codes are 9 chars: 5-char INSEE + 4-digit block. Anything
				// shorter is either a Mayotte/DROM placeholder or a parse glitch.
				if (irisCode.length() != 9) {
					skipped++;
					continue;
				}
				for (var entry : FILOSOFI_COLUMNS.entrySet()) {
					final var idx = columnIndex.get(entry.getKey());
					if (idx == null || idx >= fields.length) {
						continue;
					}
					final var raw = fields[idx].trim();
					final Double value = parseValue(raw);
					if (value == null) {
						continue;
					}
					final var spec = entry.getValue();
					rows.add(new Object[]{irisCode, spec.category.name(), spec.label, value, spec.unit, year});
					if (rows.size() >= BATCH_SIZE) {
						inserted += flush(rows);
					}
				}
			}
			inserted += flush(rows);
		}

		log.info("Filosofi IRIS import done: {} indicator rows inserted, {} IRIS rows skipped", inserted, skipped);
		return inserted;
	}

	private int flush(final ArrayList<Object[]> rows) {
		if (rows.isEmpty()) {
			return 0;
		}
		final var counts = jdbcTemplate.batchUpdate(INSERT_SQL, rows);
		rows.clear();
		return counts.length;
	}

	private static Map<String, Integer> indexHeader(final String headerLine) {
		final var idx = new LinkedHashMap<String, Integer>();
		final var fields = splitFilosofi(headerLine);
		for (int i = 0; i < fields.length; i++) {
			idx.put(fields[i].trim().replace("\"", ""), i);
		}
		return idx;
	}

	// Filosofi CSV uses ';' as separator + occasional "..." double-quoted
	// values; the columns we care about (numerics, IRIS code) never contain a
	// semicolon, so a plain split is safe and ~3x faster than a CSV library on
	// a 50k-row file.
	private static String[] splitFilosofi(final String line) {
		return line.split(";", -1);
	}

	// Filosofi marks "secret statistique" cells with "s" or "nd" rather than
	// dropping the column. Both are mapped to NULL — the cell exists, the
	// value is just legally hidden.
	private static Double parseValue(final String raw) {
		if (raw == null || raw.isBlank() || "s".equalsIgnoreCase(raw) || "nd".equalsIgnoreCase(raw)) {
			return null;
		}
		try {
			return Double.parseDouble(raw.replace(',', '.'));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private record IndicatorSpec(String label, String unit, IndicatorCategory category) {
	}
}
