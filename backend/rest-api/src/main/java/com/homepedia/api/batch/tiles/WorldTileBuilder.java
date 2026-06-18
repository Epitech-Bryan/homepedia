package com.homepedia.api.batch.tiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.homepedia.api.service.CountryGeoService;
import com.homepedia.api.service.WorldVectorTileService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Bakes the world-level GeoJSON sources (countries, admin-1, admin-2) into a
 * single {@code world.mbtiles} via tippecanoe — the same trick we use for
 * French commune polygons, just at country scale.
 *
 * <p>
 * Source files ship as classpath resources under {@code data/}:
 * <ul>
 * <li>{@code countries.geojson} — Natural Earth admin-0, served at z=0..4</li>
 * <li>{@code world-admin1.geojson} — GADM admin-1 for ~110 countries,
 * z=5..7</li>
 * <li>{@code world-admin2.geojson} — GADM admin-2 for ~66 countries,
 * z=8..10</li>
 * </ul>
 *
 * <p>
 * The pipeline copies each resource to a temp file on the writable PVC (the
 * classpath is read-only inside the jar), then invokes tippecanoe with
 * per-layer zoom bands. Output replaces the existing {@code world.mbtiles}
 * atomically and triggers {@link WorldVectorTileService#reload()} so the next
 * tile request reads the new file.
 *
 * <p>
 * Idempotent + concurrency-guarded via {@link #running}, same pattern as
 * {@link CityTileBuilder}. Initial build runs on {@link ApplicationReadyEvent}
 * if {@code world.mbtiles} is missing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "homepedia.tiles.builder.enabled", havingValue = "true", matchIfMissing = true)
public class WorldTileBuilder {

	@Value("${homepedia.tiles.world-path:/data/tiles/world.mbtiles}")
	private String worldMbtilesPath;

	@Value("${homepedia.tiles.tippecanoe-bin:tippecanoe}")
	private String tippecanoeBin;

	@Value("${homepedia.tiles.world-rebuild-on-startup:true}")
	private boolean rebuildOnStartup;

	private final WorldVectorTileService worldTileService;

	private final CountryGeoService countryGeoService;

	private final TileBuildLock tileBuildLock;

	private final ObjectMapper objectMapper;

	private final TileLayerBaker tileLayerBaker;

	private final AtomicBoolean running = new AtomicBoolean(false);

	@Async
	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		if (!rebuildOnStartup) {
			return;
		}
		final var target = Path.of(worldMbtilesPath);
		if (Files.exists(target)) {
			return;
		}
		log.info("world tile builder: mbtiles missing at {}, starting initial build", target);
		try {
			rebuild();
		} catch (Exception e) {
			log.error("initial world tile build failed: {}", e.getMessage(), e);
		}
	}

	@Async
	public void rebuildAsync() {
		try {
			rebuild();
		} catch (Exception e) {
			log.error("world tile rebuild failed: {}", e.getMessage(), e);
		}
	}

	public void rebuild() throws IOException, InterruptedException {
		if (!running.compareAndSet(false, true)) {
			log.info("world tile rebuild already running, skipping");
			return;
		}
		// Serialise against the city tile build so the two don't peak disk
		// together — acquired before any scratch is written, released in the
		// outer finally.
		tileBuildLock.acquire();
		final var start = System.currentTimeMillis();
		try {
			final var target = Path.of(worldMbtilesPath);
			Files.createDirectories(target.getParent());

			final var countriesSrc = target.resolveSibling("world-countries.src.geojson");
			final var admin1Src = target.resolveSibling("world-admin1.src.geojson");
			final var admin2Src = target.resolveSibling("world-admin2.src.geojson");
			final var admin3Src = target.resolveSibling("world-admin3.src.geojson");
			final var mbtilesTmp = target.resolveSibling("world.mbtiles.tmp");
			try {
				// Bake the same trimmed + enriched country payload the API serves
				// (code/name/population/gdp/gdpPerCapita/area) rather than the raw
				// Natural Earth file, so the countries MVT layer carries the
				// choropleth fields under the conventional names — without this
				// the layer shipped POP_EST/GDP_MD/ISO_A3 and the world choropleth
				// read nothing.
				Files.writeString(countriesSrc, countryGeoService.getCountriesGeoJson());
				// admin-1 gets its metrics baked in here so the MVT features
				// carry population/area/gdpNominal directly — the frontend
				// choropleth then reads them out of the tile properties
				// instead of needing a separate GeoJSON fetch.
				enrichAdmin1Source(admin1Src);
				materialise("data/world-admin2.geojson", admin2Src);
				// admin-3 (European communes / Gemeinden) is best-effort:
				// the resource only ships when fetched separately. Missing
				// file falls back to a 3-layer mbtiles like before.
				final var admin3Available = tryMaterialise("data/world-admin3.geojson", admin3Src);
				runTippecanoe(countriesSrc, admin1Src, admin2Src, admin3Available ? admin3Src : null, mbtilesTmp);
				Files.move(mbtilesTmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				worldTileService.reload();
				log.info("world tiles rebuilt at {} in {} ms", target, System.currentTimeMillis() - start);
			} finally {
				Files.deleteIfExists(countriesSrc);
				Files.deleteIfExists(admin1Src);
				Files.deleteIfExists(admin2Src);
				Files.deleteIfExists(admin3Src);
				Files.deleteIfExists(mbtilesTmp);
			}
		} finally {
			tileBuildLock.release();
			running.set(false);
		}
	}

	/** Returns false instead of throwing when the classpath resource is missing. */
	private boolean tryMaterialise(String classpath, Path target) {
		final var resource = new ClassPathResource(classpath);
		if (!resource.exists()) {
			return false;
		}
		try (var in = resource.getInputStream(); var out = Files.newOutputStream(target)) {
			in.transferTo(out);
			return true;
		} catch (IOException e) {
			log.warn("failed to materialise {}: {}", classpath, e.getMessage());
			return false;
		}
	}

	public boolean isRunning() {
		return running.get();
	}

	/**
	 * Copy a classpath resource to a writable file. Tippecanoe needs an actual
	 * filesystem path; the jar entries can't be passed through.
	 */
	private void materialise(String classpath, Path target) throws IOException {
		final var resource = new ClassPathResource(classpath);
		try (var in = resource.getInputStream(); var out = Files.newOutputStream(target)) {
			in.transferTo(out);
		}
	}

	/**
	 * Materialise {@code data/world-admin1.geojson} on the writable PVC, joining
	 * each feature with its {@code world-admin1-metrics.json} entry (population,
	 * area, gdpNominal) so the resulting MVT carries the choropleth values without
	 * a second roundtrip from the frontend.
	 */
	private void enrichAdmin1Source(Path target) throws IOException {
		final var metrics = loadAdmin1Metrics();
		final var resource = new ClassPathResource("data/world-admin1.geojson");
		try (var in = resource.getInputStream()) {
			final var root = (ObjectNode) objectMapper.readTree(in);
			final var features = (ArrayNode) root.get("features");
			for (JsonNode f : features) {
				final var props = (ObjectNode) f.get("properties");
				if (props == null) {
					continue;
				}
				final var code = props.path("code").asText(null);
				if (code == null) {
					continue;
				}
				final var m = metrics.get(code);
				if (m == null) {
					continue;
				}
				if (m.population != null) {
					props.put("population", m.population);
				}
				if (m.area != null) {
					props.put("area", m.area);
				}
				if (m.gdpNominal != null) {
					props.put("gdpNominal", m.gdpNominal);
					if (m.population != null && m.population > 0) {
						props.put("gdpPerCapita", m.gdpNominal / m.population);
					}
				}
			}
			Files.writeString(target, objectMapper.writeValueAsString(root), StandardCharsets.UTF_8);
		}
	}

	private record Admin1Metric(Long population, Double area, Double gdpNominal) {
	}

	private Map<String, Admin1Metric> loadAdmin1Metrics() {
		final var resource = new ClassPathResource("data/world-admin1-metrics.json");
		if (!resource.exists()) {
			log.warn("world-admin1-metrics.json missing — MVT admin-1 will ship without choropleth values");
			return Map.of();
		}
		try (var in = resource.getInputStream()) {
			final var node = (ObjectNode) objectMapper.readTree(in);
			final var out = new HashMap<String, Admin1Metric>();
			node.fields().forEachRemaining(e -> {
				final var v = e.getValue();
				final var pop = v.path("population");
				final var area = v.path("area");
				final var gdp = v.path("gdpNominal");
				out.put(e.getKey(),
						new Admin1Metric(pop.isMissingNode() || pop.isNull() ? null : pop.asLong(),
								area.isMissingNode() || area.isNull() ? null : area.asDouble(),
								gdp.isMissingNode() || gdp.isNull() ? null : gdp.asDouble()));
			});
			return out;
		} catch (IOException e) {
			log.warn("Failed to load world-admin1-metrics.json: {}", e.getMessage());
			return Map.of();
		}
	}

	/**
	 * Per-layer JSON keeps each level under its own simplification curve. Bands are
	 * disjoint so the browser only fetches one layer at any given zoom — no
	 * overlap, no double draw. Countries dominate at world view; admin-1 takes over
	 * the moment we cross into region scale; admin-2 kicks in when individual
	 * countries fill the viewport.
	 */
	private void runTippecanoe(Path countriesSrc, Path admin1Src, Path admin2Src, Path admin3Src, Path destination)
			throws IOException, InterruptedException {
		final var layers = new java.util.ArrayList<TileLayerBaker.LayerSpec>();
		layers.add(new TileLayerBaker.LayerSpec("countries", countriesSrc, 0, 4, 14));
		layers.add(new TileLayerBaker.LayerSpec("admin1", admin1Src, 5, 7, 10));
		layers.add(new TileLayerBaker.LayerSpec("admin2", admin2Src, 8, 10, 8));
		if (admin3Src != null) {
			layers.add(new TileLayerBaker.LayerSpec("admin3", admin3Src, 11, 12, 6));
		}
		tileLayerBaker.bake(layers, destination, destination.resolveSibling("world-tiles-cache"),
				List.of("--drop-densest-as-needed", "--extend-zooms-if-still-dropping", "--detect-shared-borders",
						"--maximum-tile-bytes=2000000"));
	}
}
