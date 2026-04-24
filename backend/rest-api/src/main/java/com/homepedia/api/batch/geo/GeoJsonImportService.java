package com.homepedia.api.batch.geo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homepedia.common.geo.GeoJsonBoundary;
import com.homepedia.common.geo.GeoJsonBoundaryRepository;
import com.homepedia.common.indicator.GeographicLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import static com.homepedia.common.indicator.GeographicLevel.DEPARTMENT;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoJsonImportService {

	private static final String BASE_URL = "https://raw.githubusercontent.com/gregoiredavid/france-geojson/master/";

	private final GeoJsonBoundaryRepository geoJsonBoundaryRepository;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public void importAll() {
		importRegions();
		importDepartments();
	}

	@Transactional
	public void importRegions() {
		log.info("Importing GeoJSON region boundaries...");
		final var geojson = downloadGeoJson("regions.geojson");
		final var count = importFeatures(geojson, GeographicLevel.REGION);
		log.info("Imported {} region boundaries", count);
	}

	@Transactional
	public void importDepartments() {
		log.info("Importing GeoJSON department boundaries...");
		final var geojson = downloadGeoJson("departements.geojson");
		final var count = importFeatures(geojson, DEPARTMENT);
		log.info("Imported {} department boundaries", count);
	}

	private String downloadGeoJson(String fileName) {
		log.info("Downloading {}...", fileName);
		return restClient.get().uri(BASE_URL + fileName).retrieve().body(String.class);
	}

	private int importFeatures(String geojson, GeographicLevel level) {
		try {
			final var root = objectMapper.readTree(geojson);
			final var features = root.get("features");
			var count = 0;

			for (final var feature : features) {
				final var properties = feature.get("properties");
				final var code = properties.get("code").asText();
				final var nom = properties.get("nom").asText();
				final var geometry = objectMapper.writeValueAsString(feature.get("geometry"));

				if (StringUtils.isBlank(code) || StringUtils.isBlank(nom)) {
					log.warn("Skipping feature with missing code or name");
					continue;
				}

				final var existing = geoJsonBoundaryRepository.findByGeographicLevelAndGeographicCode(level, code);

				if (existing.isPresent()) {
					final var boundary = existing.get();
					boundary.setName(nom);
					boundary.setGeometry(geometry);
					geoJsonBoundaryRepository.save(boundary);
				} else {
					geoJsonBoundaryRepository.save(GeoJsonBoundary.builder().geographicLevel(level).geographicCode(code)
							.name(nom).geometry(geometry).build());
				}
				count++;
			}

			return count;
		} catch (Exception e) {
			log.error("Failed to parse GeoJSON for level {}: {}", level, e.getMessage(), e);
			return 0;
		}
	}
}
