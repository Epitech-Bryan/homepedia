package com.homepedia.api.batch.health;

import com.homepedia.api.batch.shared.ParseUtils;
import com.homepedia.common.indicator.GeographicLevel;
import com.homepedia.common.indicator.IndicatorCategory;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthDataImportService {

	private static final int BATCH_SIZE = 1000;
	private static final String INSERT_SQL = """
			INSERT INTO indicators (geographic_level, geographic_code, category, label, indicator_value, unit, year)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbcTemplate;

	// No @Transactional: aggregation runs in memory; saves use Spring Data's
	// per-saveAll implicit transaction. Avoids holding the indicators table
	// idle-in-transaction during CSV parsing.
	public int importFromCsv(Path csvPath) throws IOException {
		log.info("Starting health data import from {}", csvPath);

		final var aggregation = new HashMap<AggregationKey, PrevalenceAccumulator>();

		try (final var reader = new BufferedReader(Files.newBufferedReader(csvPath, StandardCharsets.UTF_8))) {
			final var headerLine = reader.readLine();
			if (headerLine == null) {
				log.warn("Empty CSV file");
				return 0;
			}

			String line;
			while ((line = reader.readLine()) != null) {
				processLine(line, aggregation);
			}
		}

		log.info("Parsed health data: {} unique department/pathology/year combinations", aggregation.size());
		return saveIndicators(aggregation);
	}

	private void processLine(String line, Map<AggregationKey, PrevalenceAccumulator> aggregation) {
		final var fields = line.split(";", -1);
		if (fields.length < 12) {
			return;
		}

		final var year = ParseUtils.parseInteger(fields[0]);
		final var pathologyNiv1 = StringUtils.trimToNull(fields[1]);
		final var dept = StringUtils.trimToNull(fields[8]);
		final var prevalence = ParseUtils.parseDouble(fields[11]);

		if (year == null || StringUtils.isBlank(pathologyNiv1) || StringUtils.isBlank(dept) || prevalence == null) {
			return;
		}

		final var key = new AggregationKey(dept, pathologyNiv1, year);
		aggregation.computeIfAbsent(key, k -> new PrevalenceAccumulator()).add(prevalence);
	}

	private int saveIndicators(Map<AggregationKey, PrevalenceAccumulator> aggregation) {
		final var rows = new ArrayList<Object[]>(BATCH_SIZE);
		final var level = GeographicLevel.DEPARTMENT.name();
		final var category = IndicatorCategory.HEALTH.name();
		var count = 0;

		for (final var entry : aggregation.entrySet()) {
			final var key = entry.getKey();
			final var avgPrevalence = entry.getValue().average();
			rows.add(new Object[]{level, key.dept(), category, "Pathology: " + key.pathology(), avgPrevalence, "%",
					key.year()});

			if (rows.size() >= BATCH_SIZE) {
				jdbcTemplate.batchUpdate(INSERT_SQL, rows);
				count += rows.size();
				rows.clear();
				log.info("Saved {} health indicators...", count);
			}
		}

		if (!rows.isEmpty()) {
			jdbcTemplate.batchUpdate(INSERT_SQL, rows);
			count += rows.size();
		}

		log.info("Health data import complete: {} indicators saved", count);
		return count;
	}

	private record AggregationKey(String dept, String pathology, int year) {
	}

	private static class PrevalenceAccumulator {
		private double sum;
		private int count;

		void add(double value) {
			sum += value;
			count++;
		}

		double average() {
			return count == 0 ? 0.0 : sum / count;
		}
	}
}
