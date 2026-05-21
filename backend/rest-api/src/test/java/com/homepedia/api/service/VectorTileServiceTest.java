package com.homepedia.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit-test the mbtiles reader using a tiny SQLite file built on the fly.
 * Catches two specific regressions:
 *
 * <ol>
 * <li>The TMS↔XYZ y-flip — looking up the wrong row returns no data and the map
 * silently shows blank tiles. Build a 2x2 grid at z=1, query the top-left in
 * XYZ (y=0), assert it resolves to the row written at TMS y=1.</li>
 * <li>Missing-file handling — the service must stay in a disabled state and
 * return {@code Optional.empty()} instead of throwing, so deploying the code
 * before the file lands doesn't crash the pod.</li>
 * </ol>
 */
class VectorTileServiceTest {

	@TempDir
	Path tempDir;

	private VectorTileService service;
	private Path mbtilesPath;

	@BeforeEach
	void newService() {
		service = new VectorTileService();
		mbtilesPath = tempDir.resolve("cities.mbtiles");
	}

	@AfterEach
	void tearDown() {
		service.close();
	}

	@Test
	void missingFile_serviceStaysDisabled() {
		ReflectionTestUtils.setField(service, "citiesMbtilesPath", mbtilesPath.toString());
		service.init();

		assertThat(service.isAvailable()).isFalse();
		assertThat(service.getCityTile(10, 0, 0)).isEmpty();
	}

	@Test
	void existingFile_xyzToTmsFlip_returnsCorrectBlob() throws Exception {
		buildMiniMbtiles();
		ReflectionTestUtils.setField(service, "citiesMbtilesPath", mbtilesPath.toString());
		service.init();

		assertThat(service.isAvailable()).isTrue();

		// At z=1 the grid is 2x2. XYZ y=0 is the TOP row, which Tippecanoe
		// wrote at TMS y=1. The service must flip before the SQL lookup.
		final var topLeft = service.getCityTile(1, 0, 0);
		assertThat(topLeft).isPresent();
		assertThat(new String(topLeft.get())).isEqualTo("top-left-tile");

		// XYZ y=1 is the bottom row, TMS y=0.
		final var bottomLeft = service.getCityTile(1, 0, 1);
		assertThat(bottomLeft).isPresent();
		assertThat(new String(bottomLeft.get())).isEqualTo("bottom-left-tile");

		// Out-of-grid request resolves to empty without throwing.
		assertThat(service.getCityTile(1, 5, 5)).isEmpty();
	}

	private void buildMiniMbtiles() throws Exception {
		// Don't pre-create the file — SQLite creates it on first write
		// when opened R/W, and starting from a 0-byte file confuses the
		// immutable=1 reader the production service uses.
		try (final var c = DriverManager.getConnection("jdbc:sqlite:" + mbtilesPath.toAbsolutePath());
				final var st = c.createStatement()) {
			st.executeUpdate("CREATE TABLE tiles (zoom_level INT, tile_column INT, tile_row INT, tile_data BLOB)");
			st.executeUpdate("CREATE TABLE metadata (name TEXT, value TEXT)");
			// At z=1, 2x2 grid. tile_row uses TMS (bottom-left origin), so:
			// XYZ (0,0) top-left ↔ TMS (0, 1)
			// XYZ (0,1) bottom-left ↔ TMS (0, 0)
			try (final var ps = c.prepareStatement("INSERT INTO tiles VALUES (1, 0, 1, ?)")) {
				ps.setBytes(1, "top-left-tile".getBytes());
				ps.executeUpdate();
			}
			try (final var ps = c.prepareStatement("INSERT INTO tiles VALUES (1, 0, 0, ?)")) {
				ps.setBytes(1, "bottom-left-tile".getBytes());
				ps.executeUpdate();
			}
		}
	}
}
