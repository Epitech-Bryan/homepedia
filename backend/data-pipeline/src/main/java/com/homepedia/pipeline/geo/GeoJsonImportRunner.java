package com.homepedia.pipeline.geo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeoJsonImportRunner implements CommandLineRunner {

	private final GeoJsonImportService geoJsonImportService;

	@Value("${homepedia.geo.import-enabled:false}")
	private boolean importEnabled;

	@Override
	public void run(String... args) {
		if (!importEnabled) {
			log.info("GeoJSON boundary import disabled (homepedia.geo.import-enabled=false). Skipping.");
			return;
		}

		log.info("Starting GeoJSON boundary import...");
		geoJsonImportService.importAll();
		log.info("GeoJSON boundary import finished");
	}
}
