package com.homepedia.pipeline.dpe;

import com.homepedia.common.indicator.GeographicLevel;
import com.homepedia.common.indicator.Indicator;
import com.homepedia.common.indicator.IndicatorCategory;
import com.homepedia.common.indicator.IndicatorRepository;
import com.homepedia.pipeline.shared.ParseUtils;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DpeImportService {

	private static final int BATCH_SIZE = 1000;
	private static final String[] DPE_LABELS = {"A", "B", "C", "D", "E", "F", "G"};

	private final IndicatorRepository indicatorRepository;

	@Transactional
	public int importFromCsv(Path csvPath) throws IOException {
		log.info("Starting DPE import from {}", csvPath);

		final var aggregation = new HashMap<String, Map<String, Integer>>();
		final var totalPerCommune = new HashMap<String, Integer>();

		try (final var reader = new BufferedReader(Files.newBufferedReader(csvPath, StandardCharsets.UTF_8))) {
			final var headerLine = reader.readLine();
			if (headerLine == null) {
				log.warn("Empty CSV file");
				return 0;
			}

			String line;
			while ((line = reader.readLine()) != null) {
				final var record = parseCsvLine(line);
				if (record == null || StringUtils.isBlank(record.inseeCode())
						|| StringUtils.isBlank(record.dpeLabelEnergy())) {
					continue;
				}

				aggregation.computeIfAbsent(record.inseeCode(), k -> new HashMap<>()).merge(record.dpeLabelEnergy(), 1,
						Integer::sum);
				totalPerCommune.merge(record.inseeCode(), 1, Integer::sum);
			}
		}

		log.info("Parsed DPE data for {} communes", aggregation.size());
		return saveIndicators(aggregation, totalPerCommune);
	}

	private DpeRawRecord parseCsvLine(String line) {
		try {
			final var fields = line.split(",", -1);
			if (fields.length < 3) {
				return null;
			}

			final var inseeCode = StringUtils.trimToNull(fields[0]);
			final var dpeLabel = StringUtils.trimToNull(fields[1]);
			final var gesLabel = StringUtils.trimToNull(fields[2]);
			final var year = fields.length > 3 ? ParseUtils.parseInteger(fields[3]) : null;

			return new DpeRawRecord(inseeCode, dpeLabel, gesLabel, year);
		} catch (Exception e) {
			log.debug("Skipping invalid DPE line: {}", e.getMessage());
			return null;
		}
	}

	private int saveIndicators(Map<String, Map<String, Integer>> aggregation, Map<String, Integer> totalPerCommune) {
		final var batch = new ArrayList<Indicator>(BATCH_SIZE);
		var count = 0;

		for (final var communeEntry : aggregation.entrySet()) {
			final var inseeCode = communeEntry.getKey();
			final var total = totalPerCommune.getOrDefault(inseeCode, 0);

			if (total == 0) {
				continue;
			}

			for (final var dpeLabel : DPE_LABELS) {
				final var labelCount = communeEntry.getValue().getOrDefault(dpeLabel, 0);
				final var percentage = (labelCount * 100.0) / total;

				final var indicator = new Indicator(GeographicLevel.CITY, inseeCode, IndicatorCategory.ENERGY,
						"DPE label " + dpeLabel, percentage, "%", null);
				batch.add(indicator);

				if (batch.size() >= BATCH_SIZE) {
					indicatorRepository.saveAll(batch);
					count += batch.size();
					batch.clear();
					log.info("Saved {} DPE indicators...", count);
				}
			}
		}

		if (!batch.isEmpty()) {
			indicatorRepository.saveAll(batch);
			count += batch.size();
		}

		log.info("DPE import complete: {} indicators saved", count);
		return count;
	}
}
