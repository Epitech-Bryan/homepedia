package com.homepedia.api.batch.geocoding;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Thin client over the public BAN (Base Adresse Nationale) bulk geocoder
 * <a href="https://api-adresse.data.gouv.fr/search/csv/">/search/csv/</a>. The
 * endpoint accepts a CSV of addresses (multipart upload) and returns the same
 * CSV augmented with {@code latitude}, {@code longitude}, {@code result_score}
 * and a few other fields.
 *
 * <p>
 * BAN is rate-limited but doesn't require a key. We chunk by ~1000 rows to stay
 * well under the 50 MB request ceiling and to keep round-trip times in the
 * single-second range.
 */
@Slf4j
@Service
public class BanGeocodingService {

	private static final String BAN_CSV_URL = "https://api-adresse.data.gouv.fr/search/csv/";

	/**
	 * BAN counts addresses below this score as low-confidence. We persist them
	 * anyway (a city centre is better than nothing) but expose the score so the
	 * heatmap can opt into filtering.
	 */
	public static final double MIN_KEEP_SCORE = 0.4;

	private final RestClient restClient;

	public BanGeocodingService(RestClient.Builder restClientBuilder,
			@Value("${homepedia.ban.user-agent:homepedia/1.0}") String userAgent) {
		this.restClient = restClientBuilder.defaultHeader(HttpHeaders.USER_AGENT, userAgent).build();
	}

	/**
	 * Geocode a batch of addresses. The order of {@code addresses} is preserved in
	 * the returned list — entries that BAN failed to resolve come back with
	 * {@code latitude == null}.
	 */
	public List<GeocodingResult> geocode(List<AddressInput> addresses) {
		if (addresses == null || addresses.isEmpty()) {
			return List.of();
		}
		final var csv = buildCsv(addresses);
		final byte[] payload = csv.getBytes(StandardCharsets.UTF_8);
		final MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("data", new NamedByteArrayResource(payload, "addresses.csv"));
		// Tell BAN which columns make up the address. The endpoint defaults to
		// concatenating every column, which over-specifies the query and
		// drops the score for short addresses.
		body.add("columns", "numero");
		body.add("columns", "voie");
		body.add("columns", "code_postal");
		body.add("columns", "ville");
		body.add("postcode", "code_postal");
		final byte[] response;
		try {
			response = restClient.post().uri(BAN_CSV_URL).contentType(MediaType.MULTIPART_FORM_DATA).body(body)
					.retrieve().body(byte[].class);
		} catch (Exception e) {
			log.warn("BAN geocoding request failed for {} addresses: {}", addresses.size(), e.toString());
			return emptyResults(addresses);
		}
		if (response == null || response.length == 0) {
			log.warn("BAN returned an empty response for {} addresses", addresses.size());
			return emptyResults(addresses);
		}
		return parseResponse(addresses, response);
	}

	private static String buildCsv(List<AddressInput> addresses) {
		final var settings = new CsvWriterSettings();
		settings.getFormat().setDelimiter(';');
		settings.getFormat().setLineSeparator("\n");
		final var sw = new StringWriter();
		final var writer = new CsvWriter(sw, settings);
		writer.writeHeaders("id", "numero", "voie", "code_postal", "ville");
		for (AddressInput a : addresses) {
			writer.writeRow(a.id(), nullToEmpty(a.streetNumber()), nullToEmpty(a.street()), nullToEmpty(a.postalCode()),
					nullToEmpty(a.city()));
		}
		writer.close();
		return sw.toString();
	}

	private static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}

	private static List<GeocodingResult> parseResponse(List<AddressInput> addresses, byte[] csvBytes) {
		final var settings = new CsvParserSettings();
		settings.getFormat().setDelimiter(';');
		settings.setHeaderExtractionEnabled(true);
		settings.setMaxCharsPerColumn(8192);
		final var parser = new CsvParser(settings);
		final List<Map<String, String>> rows;
		try (var in = new ByteArrayInputStream(csvBytes)) {
			parser.beginParsing(in, StandardCharsets.UTF_8);
			final var headers = parser.getRecordMetadata().headers();
			rows = new ArrayList<>();
			String[] raw;
			while ((raw = parser.parseNext()) != null) {
				final var map = new HashMap<String, String>(headers.length);
				for (int i = 0; i < headers.length && i < raw.length; i++) {
					map.put(headers[i], raw[i]);
				}
				rows.add(map);
			}
		} catch (Exception e) {
			log.warn("Failed to parse BAN response ({} bytes): {}", csvBytes.length, e.toString());
			return emptyResults(addresses);
		}
		final Map<Long, GeocodingResult> byId = new HashMap<>(rows.size());
		for (var row : rows) {
			final var idRaw = row.get("id");
			if (idRaw == null || idRaw.isBlank())
				continue;
			final long id;
			try {
				id = Long.parseLong(idRaw.trim());
			} catch (NumberFormatException e) {
				continue;
			}
			final var lat = parseDouble(row.get("latitude"));
			final var lon = parseDouble(row.get("longitude"));
			final var score = parseDouble(row.get("result_score"));
			byId.put(id, new GeocodingResult(id, lat, lon, score));
		}
		final List<GeocodingResult> out = new ArrayList<>(addresses.size());
		for (AddressInput a : addresses) {
			out.add(byId.getOrDefault(a.id(), new GeocodingResult(a.id(), null, null, null)));
		}
		return out;
	}

	private static Double parseDouble(String s) {
		if (s == null || s.isBlank())
			return null;
		try {
			return Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static List<GeocodingResult> emptyResults(List<AddressInput> addresses) {
		final List<GeocodingResult> out = new ArrayList<>(addresses.size());
		for (AddressInput a : addresses) {
			out.add(new GeocodingResult(a.id(), null, null, null));
		}
		return out;
	}

	public record AddressInput(long id, String streetNumber, String street, String postalCode, String city) {
	}

	public record GeocodingResult(long id, Double latitude, Double longitude, Double score) {
		public boolean hasCoords() {
			return latitude != null && longitude != null;
		}
	}

	/**
	 * RestClient's multipart encoder reads the filename from
	 * {@link ByteArrayResource#getFilename()}; the default returns null and the BAN
	 * endpoint rejects the upload as "data field missing". Subclassing to pin the
	 * filename is the documented Spring workaround.
	 */
	private static final class NamedByteArrayResource extends ByteArrayResource {
		private final String filename;

		NamedByteArrayResource(byte[] bytes, String filename) {
			super(bytes);
			this.filename = filename;
		}

		@Override
		public String getFilename() {
			return filename;
		}
	}
}
