package com.homepedia.api.batch.indicator;

import com.homepedia.api.batch.shared.ParseUtils;
import com.homepedia.common.indicator.GeographicLevel;
import com.homepedia.common.indicator.Indicator;
import com.homepedia.common.indicator.IndicatorCategory;
import com.homepedia.common.indicator.IndicatorRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports indicators from a normalized CSV with header
 * <code>code_insee,year,value,label,unit</code>. The category is fixed by the
 * caller (one CSV per category). Geographic level is always CITY (commune-level
 * data); aggregation to department/region is left to downstream queries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenericIndicatorImportService {

	private static final int BATCH_SIZE = 1000;

	private final IndicatorRepository indicatorRepository;

	@Transactional
	public int importFromCsv(Path csvPath, IndicatorCategory category) throws IOException {
		log.info("Starting {} indicator import from {}", category, csvPath);

		final var batch = new ArrayList<Indicator>(BATCH_SIZE);
		var count = 0;
		var skipped = 0;

		try (final var reader = new BufferedReader(Files.newBufferedReader(csvPath, StandardCharsets.UTF_8))) {
			final var headerLine = reader.readLine();
			if (headerLine == null) {
				log.warn("Empty CSV file for {}", category);
				return 0;
			}

			String line;
			while ((line = reader.readLine()) != null) {
				final var indicator = parseLine(line, category);
				if (indicator == null) {
					skipped++;
					continue;
				}

				batch.add(indicator);

				if (batch.size() >= BATCH_SIZE) {
					indicatorRepository.saveAll(batch);
					count += batch.size();
					batch.clear();
					log.info("Saved {} {} indicators so far...", count, category);
				}
			}
		}

		if (!batch.isEmpty()) {
			indicatorRepository.saveAll(batch);
			count += batch.size();
		}

		log.info("{} import complete: {} indicators saved, {} rows skipped", category, count, skipped);
		return count;
	}

	private Indicator parseLine(String line, IndicatorCategory category) {
		try {
			final var fields = line.split(",", -1);
			if (fields.length < 3) {
				return null;
			}

			final var inseeCode = stripQuotes(StringUtils.trimToNull(fields[0]));
			final var year = ParseUtils.parseInteger(stripQuotes(fields[1]));
			final var value = ParseUtils.parseDouble(stripQuotes(fields[2]));
			final var label = fields.length > 3 ? stripQuotes(StringUtils.trimToNull(fields[3])) : null;
			final var unit = fields.length > 4 ? stripQuotes(StringUtils.trimToNull(fields[4])) : null;

			if (StringUtils.isBlank(inseeCode) || value == null || StringUtils.isBlank(label)) {
				return null;
			}

			return Indicator.builder().geographicLevel(GeographicLevel.CITY).geographicCode(inseeCode)
					.category(category).label(label).value(value).unit(unit).year(year).build();
		} catch (Exception e) {
			log.debug("Skipping invalid {} line: {}", category, e.getMessage());
			return null;
		}
	}

	private static String stripQuotes(String value) {
		if (value == null) {
			return null;
		}
		final var trimmed = value.trim();
		if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}
}
