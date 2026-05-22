package com.homepedia.api.controller;

import com.homepedia.api.batch.tiles.CityTileBuilder;
import com.homepedia.api.batch.tiles.CityTileBuilder.MetricRange;
import com.homepedia.api.service.VectorTileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mapbox Vector Tile endpoint for the commune polygons layer. Tiles come from a
 * pre-generated mbtiles file served by {@link VectorTileService}. The frontend
 * (issue #6 step 3) will eventually swap the geo.api.gouv.fr GeoJSON layer for
 * {@code L.vectorGrid.protobuf("/api/tiles/cities/{z}/{x}/{y}.pbf")} which
 * lazy-loads only the visible tiles, removing the per-department GeoJSON fetch
 * from the hot path.
 *
 * <p>
 * Tiles are immutable — only a new mbtiles file changes the data — so a 30-day
 * {@code immutable} Cache-Control directive is safe and lets the browser +
 * Traefik shared-cache do most of the work.
 *
 * <p>
 * Content is already gzipped by Tippecanoe inside the SQLite blob; we advertise
 * the encoding so clients can stream it directly to their PBF decoder without
 * double-decompression.
 */
@Tag(name = "Tiles", description = "Vector tile endpoints (MVT/PBF)")
@RestController
@RequestMapping("/tiles")
@RequiredArgsConstructor
public class TileController {

	private static final String CACHE_CONTROL_IMMUTABLE = "public, max-age=2592000, immutable";

	private final VectorTileService vectorTileService;

	// Optional: only present when the tile builder is wired in (default in
	// prod, off in dev/test). When absent, /metric-ranges responds with an
	// empty map so the frontend just falls back to its existing range
	// computation off the API stats.
	private final ObjectProvider<CityTileBuilder> cityTileBuilder;

	@Operation(summary = "Commune vector tile", description = "MVT/PBF tile of French commune polygons at the requested (z, x, y) — XYZ scheme. Returns 204 No Content when the tile is absent (sea, outside bbox) so the browser console stays clean while leaflet.vectorgrid keeps drawing the rest. Returns 400 only on malformed coordinates.")
	@GetMapping(value = "/cities/{z}/{x}/{y}.pbf", produces = "application/vnd.mapbox-vector-tile")
	public ResponseEntity<byte[]> cityTile(@Parameter(description = "Zoom level") @PathVariable final int z,
			@Parameter(description = "Tile column (XYZ)") @PathVariable final int x,
			@Parameter(description = "Tile row (XYZ)") @PathVariable final int y) {
		// Cheap guard so a malformed request can't tip the JDBC layer
		// into surprising behaviour. zoom 0..14 covers everything we
		// generate for communes.
		if (z < 0 || z > 22 || x < 0 || y < 0) {
			return ResponseEntity.badRequest().build();
		}
		return vectorTileService.getCityTile(z, x, y).map(this::okPbf).orElseGet(this::noContentPbf);
	}

	/**
	 * Empty-tile response used when {@code (z, x, y)} doesn't exist in the mbtiles
	 * (sea, polar regions, outside the France bbox). 204 instead of 404 keeps
	 * DevTools console quiet — Chrome flags 404 in red even when the JS handles it.
	 * Cache the empty answer for an hour so the browser doesn't re-ask every pan;
	 * the inner-tile distribution is fixed by the Tippecanoe pipeline so this is
	 * safe.
	 */
	private ResponseEntity<byte[]> noContentPbf() {
		return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600").build();
	}

	@Operation(summary = "Per-metric min/max for the choropleth legend", description = "Returns the global min/max across every commune for each supported metric (population, density, transactionCount, averagePrice, averagePricePerSqm). Computed once per tile rebuild and cached in memory — the response is sub-millisecond. Frontend uses these ranges to colour the vector-tile commune layer without needing to fetch /stats/cities per viewport.")
	@GetMapping("/cities/metric-ranges")
	public ResponseEntity<Map<String, MetricRange>> metricRanges() {
		final var builder = cityTileBuilder.getIfAvailable();
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
				.body(builder != null ? builder.getMetricRanges() : Map.of());
	}

	private ResponseEntity<byte[]> okPbf(final byte[] body) {
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_ENCODING, "gzip")
				.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_IMMUTABLE)
				.contentType(MediaType.parseMediaType("application/vnd.mapbox-vector-tile")).body(body);
	}
}
