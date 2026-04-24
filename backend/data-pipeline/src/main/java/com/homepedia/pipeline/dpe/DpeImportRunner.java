package com.homepedia.pipeline.dpe;

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
public class DpeImportRunner implements CommandLineRunner {

	private final DpeImportService dpeImportService;

	@Value("${homepedia.dpe.import-enabled:false}")
	private boolean importEnabled;

	@Value("${homepedia.dpe.csv-path:}")
	private String csvPath;

	@Override
	public void run(String... args) throws IOException {
		if (!importEnabled) {
			log.info("DPE import disabled (homepedia.dpe.import-enabled=false). Skipping.");
			return;
		}

		if (StringUtils.isBlank(csvPath)) {
			log.info("No DPE CSV path configured (homepedia.dpe.csv-path). Skipping DPE import.");
			return;
		}

		final var path = Path.of(csvPath);
		if (!path.toFile().exists()) {
			log.warn("DPE CSV file not found at {}. Skipping import.", csvPath);
			return;
		}

		final var count = dpeImportService.importFromCsv(path);
		log.info("DPE import finished: {} indicators loaded", count);
	}
}
