package com.homepedia.pipeline.insee;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InseeImportRunner implements CommandLineRunner {

	private final InseeImportService inseeImportService;

	@Value("${homepedia.insee.import-enabled:false}")
	private boolean importEnabled;

	@Override
	public void run(String... args) {
		if (!importEnabled) {
			log.info("INSEE import is disabled (homepedia.insee.import-enabled=false). Skipping.");
			return;
		}

		log.info("Starting INSEE data import...");
		inseeImportService.importAll();
		log.info("INSEE data import finished.");
	}
}
