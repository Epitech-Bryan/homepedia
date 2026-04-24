package com.homepedia.pipeline.health;

import java.io.IOException;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthDataImportRunner implements CommandLineRunner {

	private final HealthDataImportService healthDataImportService;

	@Value("${homepedia.health.import-enabled:false}")
	private boolean importEnabled;

	@Value("${homepedia.health.csv-path:}")
	private String csvPath;

	@Override
	public void run(String... args) throws IOException {
		if (!importEnabled) {
			log.info("Health data import disabled (homepedia.health.import-enabled=false). Skipping.");
			return;
		}

		if (StringUtils.isBlank(csvPath)) {
			log.info("No health CSV path configured (homepedia.health.csv-path). Skipping health data import.");
			return;
		}

		final var path = Path.of(csvPath);
		if (!path.toFile().exists()) {
			log.warn("Health CSV file not found at {}. Skipping import.", csvPath);
			return;
		}

		final var count = healthDataImportService.importFromCsv(path);
		log.info("Health data import finished: {} indicators loaded", count);
	}
}
