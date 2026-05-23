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
 * Same shape as {@link VectorTileService} but for the world-level mbtiles
 * (countries + admin-1 + admin-2 for ~110 countries). Kept as a separate
 * service rather than a multi-source generalisation of
 * {@link VectorTileService} so the city tile path stays untouched —
 * VectorTileService is on the hot path for every commune-zoom interaction and
 * refactoring it risks subtle latency regressions we don't want to pay just to
 * share 60 lines of JDBC plumbing.
 *
 * <p>
 * Disabled gracefully when {@code world.mbtiles} is missing: the controller
 * falls back to the existing SVG GeoJSON layers so the world view keeps
 * rendering even before the first build lands.
 */
@Slf4j
@Service
public class WorldVectorTileService {

	@Value("${homepedia.tiles.world-path:/data/tiles/world.mbtiles}")
	private String worldMbtilesPath;

	private volatile Connection connection;
	private volatile boolean available;
	private final Object reloadLock = new Object();

	@PostConstruct
	void init() {
		openOrLogMissing();
	}

	public void reload() {
		synchronized (reloadLock) {
			final var previous = connection;
			openOrLogMissing();
			if (previous != null && previous != connection) {
				try {
					previous.close();
				} catch (SQLException ignored) {
					// Same as close(): nothing actionable.
				}
			}
		}
	}

	private void openOrLogMissing() {
		final var path = Path.of(worldMbtilesPath);
		if (!Files.exists(path)) {
			log.warn("World mbtiles not found at {} — world vector tile endpoint will return 404."
					+ " Generate the file with tippecanoe to enable.", path);
			available = false;
			return;
		}
		try {
			final var config = new SQLiteConfig();
			config.setReadOnly(true);
			connection = config.createConnection("jdbc:sqlite:" + path.toAbsolutePath());
			available = true;
			log.info("World mbtiles loaded from {} ({} bytes)", path, Files.size(path));
		} catch (Exception e) {
			log.error("Failed to open world mbtiles at {}: {}", path, e.getMessage());
			available = false;
		}
	}

	@PreDestroy
	void close() {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException ignored) {
				// nothing actionable
			}
		}
	}

	public boolean isAvailable() {
		return available;
	}

	public Optional<byte[]> getWorldTile(final int z, final int x, final int y) {
		if (!available) {
			return Optional.empty();
		}
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
			log.warn("World tile lookup failed for z={} x={} y={}: {}", z, x, y, e.getMessage());
			return Optional.empty();
		}
	}
}
