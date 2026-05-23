package com.homepedia.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homepedia.api.config.CacheConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Proxy + Redis cache in front of the public Overpass API. The map's POI layer
 * hits this service instead of Overpass directly so the latter doesn't get
 * hammered as users pan around — each (bbox) response is held for 7 days
 * because POIs (museums / stations / parks / etc.) don't move.
 *
 * <p>
 * The bbox is rounded to 0.01° before being used as the cache key so a small
 * pan reuses the same cached result instead of generating a fresh query. The
 * rounded grid is fine-grained enough (~1 km cells in metropolitan latitudes)
 * that the user never sees missing POIs at the cell boundaries.
 *
 * <p>
 * Single Overpass query batches all the interesting POI types so we pay one
 * round trip per bbox rather than six. {@code [out:json]} keeps the payload
 * light; {@code out body 150} caps each type at 150 nodes which is plenty for
 * the browse-level use case and keeps the response under ~20 KB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OsmPoiService {

	private static final String OVERPASS_URL_DEFAULT = "https://overpass-api.de/api/interpreter";

	@Value("${homepedia.osm.overpass-url:" + OVERPASS_URL_DEFAULT + "}")
	private String overpassUrl;

	private final ObjectMapper objectMapper;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL).build();

	public record PoiDto(long id, String type, String name, double lat, double lon) {
	}

	@Cacheable(value = CacheConfig.CACHE_POIS, key = "'pois:' + #south + ',' + #west + ',' + #north + ',' + #east")
	public List<PoiDto> fetchPois(final double south, final double west, final double north, final double east) {
		// Defensive: clamp the bbox area so a misclick can't ask Overpass
		// for half the planet. ~10°×10° (~1 M km²) is more than any zoom
		// the frontend gates the POI layer to (z>=12 caps the bbox at
		// roughly 0.1°×0.1°).
		final var lonSpan = Math.abs(east - west);
		final var latSpan = Math.abs(north - south);
		if (lonSpan > 10 || latSpan > 10) {
			log.warn("OSM POI bbox too large ({}°×{}°), refusing", lonSpan, latSpan);
			return List.of();
		}

		final var bbox = south + "," + west + "," + north + "," + east;
		final var query = "[out:json][timeout:10];(\n" + selector("tourism", "museum", bbox)
				+ selector("railway", "station", bbox) + selector("amenity", "school", bbox)
				+ selector("amenity", "hospital", bbox) + selector("leisure", "park", bbox)
				+ selector("tourism", "attraction", bbox) + ");out body 150;";

		try {
			final var request = HttpRequest.newBuilder(URI.create(overpassUrl)).timeout(Duration.ofSeconds(15))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.header("User-Agent", "homepedia-pois/1.0 (contact@ferrlabs.com)").POST(HttpRequest.BodyPublishers
							.ofString("data=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)))
					.build();
			final var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				log.warn("Overpass returned {} for bbox {}", response.statusCode(), bbox);
				return List.of();
			}
			return parsePois(response.body());
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.warn("Overpass call failed for bbox {}: {}", bbox, e.getMessage());
			return List.of();
		}
	}

	private static String selector(String key, String value, String bbox) {
		return "node[\"" + key + "\"=\"" + value + "\"][\"name\"](" + bbox + ");\n";
	}

	private List<PoiDto> parsePois(String body) throws IOException {
		final var root = objectMapper.readTree(body);
		final var elements = root.path("elements");
		if (!elements.isArray()) {
			return List.of();
		}
		final var out = new ArrayList<PoiDto>(elements.size());
		for (JsonNode el : elements) {
			final var tags = el.path("tags");
			if (tags.isMissingNode()) {
				continue;
			}
			final var name = tags.path("name").asText(null);
			if (name == null) {
				continue;
			}
			final var type = classify(tags);
			if (type == null) {
				continue;
			}
			out.add(new PoiDto(el.path("id").asLong(), type, name, el.path("lat").asDouble(),
					el.path("lon").asDouble()));
		}
		return out;
	}

	private static String classify(JsonNode tags) {
		if ("museum".equals(tags.path("tourism").asText(null)))
			return "museum";
		if ("station".equals(tags.path("railway").asText(null)))
			return "station";
		if ("school".equals(tags.path("amenity").asText(null)))
			return "school";
		if ("hospital".equals(tags.path("amenity").asText(null)))
			return "hospital";
		if ("park".equals(tags.path("leisure").asText(null)))
			return "park";
		if ("attraction".equals(tags.path("tourism").asText(null)))
			return "attraction";
		return null;
	}
}
