package com.homepedia.api.batch.dpe;

import com.homepedia.api.batch.shared.ParseUtils;
import com.homepedia.common.indicator.GeographicLevel;
import com.homepedia.common.indicator.Indicator;
import com.homepedia.common.indicator.IndicatorCategory;
import com.homepedia.common.indicator.IndicatorRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class DpeImportService {

	private static final int BATCH_SIZE = 1000;
	private static final String[] DPE_LABELS = {"A", "B", "C", "D", "E", "F", "G"};
	private static final Pattern LINK_NEXT_PATTERN = Pattern.compile("<([^>]+)>;\\s*rel=\"?next\"?");

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

	@Transactional
	public int importFromApi(String baseUrl, RestClient restClient) {
		log.info("Starting DPE import from API: {}", baseUrl);

		final var aggregation = new HashMap<String, Map<String, Integer>>();
		final var totalPerCommune = new HashMap<String, Integer>();

		var currentUrl = baseUrl;
		var pageCount = 0;
		var rowCount = 0;
		var isFirstPage = true;

		while (currentUrl != null) {
			pageCount++;
			final var url = currentUrl;

			final var result = restClient.get().uri(URI.create(url)).exchange((request, response) -> {
				final var body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
				final var linkHeader = response.getHeaders().getFirst(HttpHeaders.LINK);
				return new PageResult(body, parseNextUrl(linkHeader));
			});

			try (final var reader = new BufferedReader(new StringReader(result.body()))) {
				String line;
				var lineIndex = 0;
				while ((line = reader.readLine()) != null) {
					if (lineIndex == 0 && isFirstPage) {
						lineIndex++;
						continue;
					}
					lineIndex++;

					final var record = parseCsvLine(line);
					if (record == null || StringUtils.isBlank(record.inseeCode())
							|| StringUtils.isBlank(record.dpeLabelEnergy())) {
						continue;
					}

					aggregation.computeIfAbsent(record.inseeCode(), k -> new HashMap<>()).merge(record.dpeLabelEnergy(),
							1, Integer::sum);
					totalPerCommune.merge(record.inseeCode(), 1, Integer::sum);
					rowCount++;
				}
			} catch (IOException e) {
				log.error("Error reading API response page {}: {}", pageCount, e.getMessage());
				break;
			}

			if (pageCount % 100 == 0) {
				log.info("Processed {} pages, {} rows so far...", pageCount, rowCount);
			}

			isFirstPage = false;
			currentUrl = result.nextUrl();
		}

		log.info("Parsed DPE API data: {} pages, {} rows, {} communes", pageCount, rowCount, aggregation.size());
		return saveIndicators(aggregation, totalPerCommune);
	}

	private static String parseNextUrl(String linkHeader) {
		if (StringUtils.isBlank(linkHeader)) {
			return null;
		}
		final var matcher = LINK_NEXT_PATTERN.matcher(linkHeader);
		return matcher.find() ? matcher.group(1) : null;
	}

	private DpeRawRecord parseCsvLine(String line) {
		try {
			final var fields = line.split(",", -1);
			if (fields.length < 3) {
				return null;
			}

			final var inseeCode = stripQuotes(StringUtils.trimToNull(fields[0]));
			final var dpeLabel = stripQuotes(StringUtils.trimToNull(fields[1]));
			final var gesLabel = stripQuotes(StringUtils.trimToNull(fields[2]));
			final var year = fields.length > 3 ? ParseUtils.parseInteger(stripQuotes(fields[3])) : null;

			return new DpeRawRecord(inseeCode, dpeLabel, gesLabel, year);
		} catch (Exception e) {
			log.debug("Skipping invalid DPE line: {}", e.getMessage());
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

				final var indicator = Indicator.builder().geographicLevel(GeographicLevel.CITY)
						.geographicCode(inseeCode).category(IndicatorCategory.ENERGY).label("DPE label " + dpeLabel)
						.value(percentage).unit("%").build();
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

	private record PageResult(String body, String nextUrl) {
	}
}
