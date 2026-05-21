package com.homepedia.api.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.sqlite.SQLiteConfig;

/**
 * Serves Mapbox Vector Tile (MVT) PBF blobs out of a pre-generated mbtiles
 * file. mbtiles is just SQLite — one row per (z, x, y) holding the gzipped
 * protobuf — so a tiny JDBC reader is enough, no separate tile server pod
 * required.
 *
 * <p>
 * The file is expected at {@code homepedia.tiles.cities-path} (defaults to
 * {@code /data/tiles/cities.mbtiles}). If it's missing the service stays in a
 * disabled state and the controller responds 404 to every tile request — that
 * way deploying the code before the file lands (or with the file mounted on a
 * dedicated PVC) doesn't crash the pod.
 *
 * <p>
 * The mbtiles schema uses TMS y-coordinates (origin bottom-left) while
 * Leaflet/Mapbox use XYZ (origin top-left). The controller flips y before
 * looking up here so callers can pass XYZ all the way through.
 *
 * <p>
 * Connection is held open for the lifetime of the bean — SQLite handles
 * read-only concurrent queries fine, and a single shared connection is cheaper
 * than opening one per tile.
 */
@Slf4j
@Service
public class VectorTileService {

	@Value("${homepedia.tiles.cities-path:/data/tiles/cities.mbtiles}")
	private String citiesMbtilesPath;

	private Connection connection;
	private boolean available;

	@PostConstruct
	void init() {
		final var path = Path.of(citiesMbtilesPath);
		if (!Files.exists(path)) {
			log.warn("Cities mbtiles not found at {} — vector tile endpoint will return 404. Generate the file with"
					+ " tippecanoe and mount it at this path to enable.", path);
			available = false;
			return;
		}
		try {
			// Use SQLiteConfig for read-only — passing options through the
			// JDBC URL with xerial requires the `file:` URI prefix, which
			// is brittle. The Config API works the same in prod and in the
			// unit test that builds the mbtiles on the fly.
			final var config = new SQLiteConfig();
			config.setReadOnly(true);
			connection = config.createConnection("jdbc:sqlite:" + path.toAbsolutePath());
			available = true;
			log.info("Cities mbtiles loaded from {} ({} bytes)", path, Files.size(path));
		} catch (Exception e) {
			log.error("Failed to open mbtiles at {}: {}", path, e.getMessage());
			available = false;
		}
	}

	@PreDestroy
	void close() {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException ignored) {
				// Closing a SQLite read-only connection rarely fails;
				// nothing actionable if it does.
			}
		}
	}

	public boolean isAvailable() {
		return available;
	}

	/**
	 * Look up the PBF blob for the given XYZ tile coordinates. Returns
	 * {@link Optional#empty()} when the tile is absent (sea, polar regions, outside
	 * the source bbox) or when the mbtiles file isn't available.
	 */
	public Optional<byte[]> getCityTile(final int z, final int x, final int y) {
		if (!available) {
			return Optional.empty();
		}
		// Flip from XYZ (top-left origin) to TMS (bottom-left) for the
		// mbtiles SQL schema — Tippecanoe writes TMS y by default.
		final var tmsY = (1 << z) - 1 - y;
		try (final var ps = connection
				.prepareStatement("SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?")) {
			ps.setInt(1, z);
			ps.setInt(2, x);
			ps.setInt(3, tmsY);
			try (final var rs = ps.executeQuery()) {
				if (!rs.next()) {
					return Optional.empty();
				}
				return Optional.ofNullable(rs.getBytes(1));
			}
		} catch (SQLException e) {
			log.warn("Tile lookup failed for z={} x={} y={}: {}", z, x, y, e.getMessage());
			return Optional.empty();
		}
	}
}
