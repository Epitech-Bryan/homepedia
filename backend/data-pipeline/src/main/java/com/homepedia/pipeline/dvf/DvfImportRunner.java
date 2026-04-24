package com.homepedia.pipeline.dvf;

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
public class DvfImportRunner implements CommandLineRunner {

	private final DvfImportService dvfImportService;

	@Value("${homepedia.dvf.zip-path:}")
	private String dvfZipPath;

	@Override
	public void run(String... args) throws IOException {
		if (StringUtils.isBlank(dvfZipPath)) {
			log.info("No DVF zip path configured (homepedia.dvf.zip-path). Skipping DVF import.");
			return;
		}

		final var path = Path.of(dvfZipPath);
		if (!path.toFile().exists()) {
			log.warn("DVF zip file not found at {}. Skipping import.", dvfZipPath);
			return;
		}

		final var count = dvfImportService.importFromZip(path);
		log.info("DVF import finished: {} transactions loaded", count);
	}
}
