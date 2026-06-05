package com.homepedia.api.batch.tiles;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homepedia.api.batch.tiles.CityTileStatsRepository.CityTileStatsProjection;
import com.homepedia.api.service.VectorTileService;
import com.homepedia.common.stats.StatsRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Rebuilds {@code cities.mbtiles} with per-commune stats baked into each
 * polygon's MVT feature properties — replaces the {@code GET /stats/cities}
 * per-pan call that today blocks the choropleth at low zoom.
 *
 * <p>
 * Pipeline, each step is no-op safe so the pod can restart mid-build:
 * <ol>
 * <li>Ensure a source polygons GeoJSON exists at
 * {@code homepedia.tiles.source-geojson}. Fetches per-department from
 * geo.api.gouv.fr on first run, ~50 MB total. The PVC keeps it across pod
 * restarts.</li>
 * <li>Stream the polygons, join each feature with its row from
 * {@link CityTileStatsRepository#findAllForTiles()} keyed by
 * {@code feature.properties.code}, write the enriched FeatureCollection to a
 * temp file alongside the target so the rename is atomic.</li>
 * <li>Invoke {@code tippecanoe} via {@link ProcessBuilder} to produce a new
 * mbtiles next to the current one. Same zoom range + simplification as
 * {@code docs/vector-tiles.md} so feature.id stability isn't affected.</li>
 * <li>{@link java.nio.file.Files#move} with
 * {@link StandardCopyOption#ATOMIC_MOVE} so the rest-api's open SQLite
 * connection sees a coherent file flip, then ask
 * {@link VectorTileService#reload()} to re-open against it.</li>
 * </ol>
 *
 * <p>
 * Triggered at boot if the mbtiles is missing or older than the source, and
 * after every DVF import (chained off {@code BatchScheduler}). Concurrent
 * triggers are coalesced via {@link #running} so a slow rebuild doesn't queue
 * up tens of pending runs while DVF jobs back to back.
 *
 * <p>
 * Disabled via {@code homepedia.tiles.builder.enabled=false} for environments
 * that don't have the tippecanoe binary on PATH (dev profile, test runners).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "homepedia.tiles.builder.enabled", havingValue = "true", matchIfMissing = true)
public class CityTileBuilder {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final JsonFactory JSON_FACTORY = new JsonFactory();

	// Metropolitan France (01..95, excluding 20 which is split into 2A/2B for
	// admin codes but geo.api still exposes "20") + Corsica (2A, 2B) +
	// overseas departments (971..976). Same set the geo.api.gouv.fr
	// {@code GET /departements} endpoint enumerates.
	private static final List<String> DEPARTMENTS = buildDepartmentCodes();

	// Communes that publish municipal arrondissements through geo.api.gouv.fr
	// (Paris, Marseille, Lyon). At commune zoom the frontend wants the 20/16/9
	// arrondissement polygons rather than a single giant box covering the
	// whole city — those polygons are richer for the DVF heatmap and match
	// what the in-app drilldown already shows when MVT is off.
	private static final java.util.Map<String, String> ARRONDISSEMENT_PARENTS = java.util.Map.of("75", "75056", "13",
			"13055", "69", "69123");

	@Value("${homepedia.tiles.cities-path:/data/tiles/cities.mbtiles}")
	private String mbtilesPath;

	@Value("${homepedia.tiles.source-geojson:/data/tiles/communes-fr.geojson}")
	private String sourceGeojsonPath;

	@Value("${homepedia.tiles.regions-geojson:/data/tiles/regions-fr.geojson}")
	private String regionsGeojsonPath;

	@Value("${homepedia.tiles.departments-geojson:/data/tiles/departments-fr.geojson}")
	private String departmentsGeojsonPath;

	@Value("${homepedia.tiles.iris-geojson:/data/tiles/iris-fr.geojson}")
	private String irisGeojsonPath;

	@Value("${homepedia.tiles.geo-api-base-url:https://geo.api.gouv.fr}")
	private String geoApiBaseUrl;

	// geo.api.gouv.fr's /regions and /departements endpoints regressed in
	// 2026: they now ignore `format=geojson&geometry=contour` and return a
	// bare JSON array with no geometry, which breaks our FeatureCollection
	// parser. Fall back to the same static GeoJSON source that
	// GeoJsonImportService consumes — stable, exact shape we need.
	@Value("${homepedia.tiles.geojson-fallback-base-url:https://raw.githubusercontent.com/gregoiredavid/france-geojson/master}")
	private String geojsonFallbackBaseUrl;

	@Value("${homepedia.tiles.tippecanoe-bin:tippecanoe}")
	private String tippecanoeBin;

	@Value("${homepedia.tiles.rebuild-on-startup:true}")
	private boolean rebuildOnStartup;

	private final CityTileStatsRepository statsRepository;

	private final StatsRepository adminStatsRepository;

	private final VectorTileService vectorTileService;

	private final JdbcTemplate jdbcTemplate;

	private final TileBuildLock tileBuildLock;

	private final AtomicBoolean running = new AtomicBoolean(false);

	// Per-metric min/max across all communes, recomputed during enrichment.
	// Frontend reads this through {@link TileController#metricRanges} so the
	// choropleth legend can size itself without a {@code /stats/cities} round
	// trip the size of the visible viewport. Volatile: the writer is the
	// rebuild thread, the readers are HTTP request threads.
	private volatile Map<String, MetricRange> metricRanges = Map.of();

	public Map<String, MetricRange> getMetricRanges() {
		return metricRanges;
	}

	/**
	 * Persist {@link #metricRanges} to JSON next to the mbtiles so a pod restart
	 * doesn't blank the choropleth — without this, the field resets to {@code
	 * Map.of()} on every boot and the layer paints everything gray until the next
	 * DVF import triggers a rebuild. Load is best-effort: a missing or corrupt file
	 * falls back to "no ranges known" and the choropleth uses its frontend-derived
	 * fallback.
	 */
	private Path metricRangesPath() {
		return Path.of(mbtilesPath).resolveSibling("metric-ranges.json");
	}

	@jakarta.annotation.PostConstruct
	void loadPersistedRanges() {
		final var path = metricRangesPath();
		if (!Files.exists(path)) {
			return;
		}
		try (var in = Files.newInputStream(path)) {
			final var typeRef = MAPPER.getTypeFactory().constructMapType(java.util.LinkedHashMap.class,
					MAPPER.getTypeFactory().constructType(String.class),
					MAPPER.getTypeFactory().constructType(MetricRange.class));
			final Map<String, MetricRange> loaded = MAPPER.readValue(in, typeRef);
			metricRanges = Map.copyOf(loaded);
			log.info("loaded {} metric ranges from {}", metricRanges.size(), path);
		} catch (IOException e) {
			log.warn("failed to load metric-ranges.json: {}", e.getMessage());
		}
	}

	private void persistRanges() {
		final var path = metricRangesPath();
		try (var out = Files.newOutputStream(path)) {
			MAPPER.writerWithDefaultPrettyPrinter().writeValue(out, metricRanges);
		} catch (IOException e) {
			log.warn("failed to persist metric-ranges.json: {}", e.getMessage());
		}
	}

	/**
	 * Min/max of a metric across every commune plus 6 internal quantile
	 * break-points (matching the 7-color choropleth palette). With one Paris
	 * outlier at 2.1 M and a long tail of villages under 1 k, the linear (min..max)
	 * ratio painted everything beige; quantile binning gives each colour ~1/7 of
	 * the communes so the gradient actually reads.
	 */
	public record MetricRange(Double min, Double max, java.util.List<Double> breaks) {
	}

	// ApplicationReadyEvent + @Async dispatches through the Spring proxy so
	// rebuild() actually runs off the main thread. Wiring this on
	// @PostConstruct + this.rebuildAsync() bypasses the proxy (classic
	// self-invocation gotcha) and would block startup until ~50 MB of
	// polygons are fetched and tippecanoe finishes — the readiness probe
	// fails long before that.
	@Async
	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		// Even when the mbtiles is fresh, the in-memory metricRanges starts
		// empty after a pod restart — recompute from the DB so the choropleth
		// has its ranges without waiting for the next DVF import.
		if (metricRanges.isEmpty() && Files.exists(Path.of(mbtilesPath))) {
			try {
				final var stats = loadStatsByCode();
				metricRanges = computeRanges(stats.values());
				persistRanges();
				log.info("rebuilt metric ranges from DB for existing mbtiles ({} metrics)", metricRanges.size());
			} catch (Exception e) {
				log.warn("failed to rebuild metric ranges on startup: {}", e.getMessage());
			}
		}
		if (!rebuildOnStartup) {
			return;
		}
		final var target = Path.of(mbtilesPath);
		if (Files.exists(target)) {
			return;
		}
		log.info("city tile builder: mbtiles missing at {}, starting initial build", target);
		try {
			rebuild();
		} catch (Exception e) {
			log.error("initial city tile build failed: {}", e.getMessage(), e);
		}
	}

	/**
	 * Fire-and-forget entry point — safe to call from a scheduler or a job
	 * listener. Skips if a build is already in flight.
	 */
	@Async
	public void rebuildAsync() {
		try {
			rebuild();
		} catch (Exception e) {
			log.error("city tile rebuild failed: {}", e.getMessage(), e);
		}
	}

	/**
	 * Synchronous rebuild. Surfaces the underlying exception so callers (admin
	 * endpoint, tests) can fail loudly.
	 */
	public void rebuild() throws IOException, InterruptedException {
		if (!running.compareAndSet(false, true)) {
			log.info("city tile rebuild already running, skipping");
			return;
		}
		// Serialise against the world tile build so the two don't peak disk
		// together (see TileBuildLock).
		tileBuildLock.acquire();
		final var start = System.currentTimeMillis();
		try {
			final var communesSrc = Path.of(sourceGeojsonPath);
			final var regionsSrc = Path.of(regionsGeojsonPath);
			final var departmentsSrc = Path.of(departmentsGeojsonPath);
			final var irisSrc = Path.of(irisGeojsonPath);
			ensurePolygonSource(communesSrc);
			ensureRegionsSource(regionsSrc);
			ensureDepartmentsSource(departmentsSrc);
			// IRIS is best-effort: if the Filosofi importer hasn't populated
			// geo_boundaries yet, we just skip the layer rather than failing
			// the whole rebuild. The frontend treats an absent iris layer the
			// same as one with no features.
			final var irisAvailable = ensureIrisSource(irisSrc);

			final var target = Path.of(mbtilesPath);
			Files.createDirectories(target.getParent());

			final var enrichedTmp = target.resolveSibling("communes-enriched.geojson.tmp");
			final var regionsEnrichedTmp = target.resolveSibling("regions-enriched.geojson.tmp");
			final var departmentsEnrichedTmp = target.resolveSibling("departments-enriched.geojson.tmp");
			final var mbtilesTmp = target.resolveSibling("cities.mbtiles.tmp");

			try {
				final var stats = loadStatsByCode();
				metricRanges = computeRanges(stats.values());
				persistRanges();
				enrich(communesSrc, enrichedTmp, stats);
				enrichAdmin(regionsSrc, regionsEnrichedTmp, loadRegionStats());
				enrichAdmin(departmentsSrc, departmentsEnrichedTmp, loadDepartmentStats());
				runTippecanoe(regionsEnrichedTmp, departmentsEnrichedTmp, enrichedTmp, irisAvailable ? irisSrc : null,
						mbtilesTmp);
				Files.move(mbtilesTmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				vectorTileService.reload();
				log.info("city tiles rebuilt at {} in {} ms", target, System.currentTimeMillis() - start);
			} finally {
				Files.deleteIfExists(enrichedTmp);
				Files.deleteIfExists(regionsEnrichedTmp);
				Files.deleteIfExists(departmentsEnrichedTmp);
				Files.deleteIfExists(mbtilesTmp);
			}
		} finally {
			tileBuildLock.release();
			running.set(false);
		}
	}

	/** Visible for tests / admin endpoint to know whether a build is in flight. */
	public boolean isRunning() {
		return running.get();
	}

	private Map<String, MetricRange> computeRanges(java.util.Collection<CityTileStatsProjection> rows) {
		final var ranges = new HashMap<String, MetricRange>();
		ranges.put("population", rangeOf(rows, p -> nullableDouble(p.getPopulation())));
		ranges.put("transactionCount", rangeOf(rows, p -> nullableDouble(p.getTransactionCount())));
		ranges.put("averagePrice", rangeOf(rows, CityTileStatsProjection::getAveragePrice));
		ranges.put("averagePricePerSqm", rangeOf(rows, CityTileStatsProjection::getAveragePricePerSqm));
		ranges.put("pollutionScore", rangeOf(rows, CityTileStatsProjection::getPollutionScore));
		// Density isn't a stored field — it's population / area. Compute on
		// the fly so the frontend reads it through the same channel.
		ranges.put("density", rangeOf(rows, p -> {
			final var pop = p.getPopulation();
			final var area = p.getArea();
			return pop != null && area != null && area > 0 ? pop.doubleValue() / area : null;
		}));
		return Map.copyOf(ranges);
	}

	private static Double nullableDouble(Number n) {
		return n != null ? n.doubleValue() : null;
	}

	private static MetricRange rangeOf(java.util.Collection<CityTileStatsProjection> rows,
			java.util.function.Function<CityTileStatsProjection, Double> extractor) {
		final var values = new java.util.ArrayList<Double>(rows.size());
		for (final var r : rows) {
			final var v = extractor.apply(r);
			if (v != null && Double.isFinite(v) && v > 0) {
				values.add(v);
			}
		}
		if (values.isEmpty()) {
			return new MetricRange(null, null, java.util.List.of());
		}
		java.util.Collections.sort(values);
		final var min = values.get(0);
		final var max = values.get(values.size() - 1);
		// 6 internal break-points partition the sorted values into 7 equal-
		// sized buckets — one per colour in CHOROPLETH_SCALE. Using indices
		// rather than interpolated percentiles avoids floating-point drift
		// when the frontend bisects: every commune ends up in exactly one
		// bucket and Paris stops painting France beige.
		final var breaks = new java.util.ArrayList<Double>(6);
		for (int i = 1; i <= 6; i++) {
			final var idx = (int) Math.round((double) i / 7.0 * (values.size() - 1));
			breaks.add(values.get(idx));
		}
		return new MetricRange(min, max, java.util.List.copyOf(breaks));
	}

	private Map<String, CityTileStatsProjection> loadStatsByCode() {
		final var rows = statsRepository.findAllForTiles();
		final var byCode = new HashMap<String, CityTileStatsProjection>(rows.size() * 2);
		for (final var r : rows) {
			byCode.put(r.getCode(), r);
		}
		log.info("loaded stats for {} communes from city_dvf_yearly_stats", byCode.size());
		return byCode;
	}

	/**
	 * Stream-rewrites the FeatureCollection so we never hold every feature in
	 * memory at once (the commune file is ~50 MB; the in-memory Jackson tree would
	 * be 3-4× that). For each feature we read the existing properties, apply the
	 * supplied merge, and emit. Order is preserved so tippecanoe sees the same
	 * feature ordering as before.
	 */
	private void rewriteFeatures(Path source, Path destination, Consumer<Map<String, Object>> merge)
			throws IOException {
		try (var in = Files.newInputStream(source);
				var parser = JSON_FACTORY.createParser(in);
				var out = Files.newOutputStream(destination);
				var generator = JSON_FACTORY.createGenerator(out)) {
			generator.setCodec(MAPPER);
			parser.setCodec(MAPPER);

			expect(parser.nextToken(), JsonToken.START_OBJECT);
			generator.writeStartObject();

			while (parser.nextToken() != JsonToken.END_OBJECT) {
				final var field = parser.currentName();
				parser.nextToken();
				if ("features".equals(field)) {
					generator.writeArrayFieldStart("features");
					expect(parser.currentToken(), JsonToken.START_ARRAY);
					var enriched = 0;
					while (parser.nextToken() != JsonToken.END_ARRAY) {
						final var feature = MAPPER.<Map<String, Object>>readValue(parser,
								MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
						merge.accept(feature);
						MAPPER.writeValue(generator, feature);
						enriched++;
					}
					generator.writeEndArray();
					log.info("enriched {} features into {}", enriched, destination.getFileName());
				} else {
					// Pass through `type`, `crs`, etc. untouched.
					generator.writeFieldName(field);
					MAPPER.writeValue(generator, MAPPER.readTree(parser));
				}
			}
			generator.writeEndObject();
		}
	}

	private void enrich(Path source, Path destination, Map<String, CityTileStatsProjection> statsByCode)
			throws IOException {
		rewriteFeatures(source, destination, feature -> mergeStats(feature, statsByCode));
	}

	private void enrichAdmin(Path source, Path destination, Map<String, AdminDvfStats> statsByCode) throws IOException {
		rewriteFeatures(source, destination, feature -> mergeAdminStats(feature, statsByCode));
	}

	@SuppressWarnings("unchecked")
	private void mergeStats(Map<String, Object> feature, Map<String, CityTileStatsProjection> statsByCode) {
		final var properties = (Map<String, Object>) feature.get("properties");
		if (properties == null) {
			return;
		}
		final var code = (String) properties.get("code");
		if (code == null) {
			return;
		}
		final var stats = statsByCode.get(code);
		if (stats == null) {
			return;
		}
		// geo.api.gouv.fr already provides `population` and `surface` (in
		// hectares). Our DB has population (more authoritative on recent
		// INSEE releases) and area in km². Write the DB values under explicit
		// keys so the frontend reads them deterministically; keep the
		// geo.api `surface` field intact for backward compat readers.
		putIfNonNull(properties, "population", stats.getPopulation());
		putIfNonNull(properties, "areaKm2", stats.getArea());
		properties.put("transactionCount", stats.getTransactionCount());
		putIfNonNull(properties, "averagePrice", stats.getAveragePrice());
		putIfNonNull(properties, "averagePricePerSqm", stats.getAveragePricePerSqm());
		putIfNonNull(properties, "pollutionScore", stats.getPollutionScore());
	}

	private static void putIfNonNull(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		}
	}

	@SuppressWarnings("unchecked")
	private void mergeAdminStats(Map<String, Object> feature, Map<String, AdminDvfStats> statsByCode) {
		final var properties = (Map<String, Object>) feature.get("properties");
		if (properties == null) {
			return;
		}
		final var code = (String) properties.get("code");
		if (code == null) {
			return;
		}
		final var stats = statsByCode.get(code);
		if (stats == null) {
			return;
		}
		putIfNonNull(properties, "population", stats.population());
		putIfNonNull(properties, "areaKm2", stats.area());
		properties.put("transactionCount", stats.transactionCount());
		putIfNonNull(properties, "averagePrice", stats.averagePrice());
		putIfNonNull(properties, "averagePricePerSqm", stats.averagePricePerSqm());
	}

	private Map<String, AdminDvfStats> loadRegionStats() {
		final var map = new HashMap<String, AdminDvfStats>();
		for (final var r : adminStatsRepository.aggregateRegionStats()) {
			map.put(r.getCode(), new AdminDvfStats(r.getPopulation(), r.getArea(), r.getTransactionCount(),
					r.getAveragePrice(), r.getAveragePricePerSqm()));
		}
		log.info("loaded DVF aggregates for {} regions", map.size());
		return map;
	}

	private Map<String, AdminDvfStats> loadDepartmentStats() {
		final var map = new HashMap<String, AdminDvfStats>();
		for (final var d : adminStatsRepository.aggregateDepartmentStats(null)) {
			map.put(d.getCode(), new AdminDvfStats(d.getPopulation(), d.getArea(), d.getTransactionCount(),
					d.getAveragePrice(), d.getAveragePricePerSqm()));
		}
		log.info("loaded DVF aggregates for {} departments", map.size());
		return map;
	}

	private record AdminDvfStats(Long population, Double area, Long transactionCount, Double averagePrice,
			Double averagePricePerSqm) {
	}

	private static void expect(JsonToken actual, JsonToken expected) throws IOException {
		if (actual != expected) {
			throw new IOException("expected " + expected + " but got " + actual);
		}
	}

	/**
	 * Fetch the 13 régions / 101 départements GeoJSON once from
	 * {@code geo.api.gouv.fr} and cache on the PVC. Refetching costs ~50 KB for
	 * regions and ~250 KB for departments — cheap, but skipping on a present file
	 * lets the pod restart instantly. Both endpoints already carry
	 * {@code code, nom, population, surface} so no further enrichment is needed
	 * before tippecanoe.
	 */
	private void ensureRegionsSource(Path source) throws IOException, InterruptedException {
		if (Files.exists(source)) {
			return;
		}
		log.info("regions source missing at {}, fetching", source);
		Files.createDirectories(source.getParent());
		fetchGeoApiCollection(URI.create(geojsonFallbackBaseUrl + "/regions.geojson"), source);
	}

	private void ensureDepartmentsSource(Path source) throws IOException, InterruptedException {
		if (Files.exists(source)) {
			return;
		}
		log.info("departments source missing at {}, fetching", source);
		Files.createDirectories(source.getParent());
		fetchGeoApiCollection(URI.create(geojsonFallbackBaseUrl + "/departements.geojson"), source);
	}

	/**
	 * Stream every IRIS polygon out of {@code geo_boundaries} (where the Filosofi
	 * importer parks them) into a FeatureCollection on disk for tippecanoe to
	 * consume. Returns {@code false} when the table holds no IRIS rows yet — the
	 * rebuild then proceeds without the iris layer rather than failing.
	 *
	 * <p>
	 * Uses {@link JdbcTemplate#query(String, java.sql.PreparedStatement)} with a
	 * {@code fetchSize} so the ~50 k rows stream through Postgres' cursor protocol
	 * instead of materialising the full result set in memory — critical because
	 * each {@code geometry} column can be tens of KB of GeoJSON. Each row is
	 * written straight to the output stream, never held in a Java collection.
	 *
	 * <p>
	 * Properties emitted per feature: {@code code} (9-char IRIS), {@code name}
	 * (label), {@code commune_code} (first 5 chars — what the frontend click
	 * handler routes to). No stats baked yet; the iris layer renders as a neutral
	 * fill that inherits its parent commune's colour via the shared styleFor
	 * closure on the frontend.
	 */
	private boolean ensureIrisSource(Path source) throws IOException {
		Files.createDirectories(source.getParent());
		final var tmp = source.resolveSibling(source.getFileName() + ".tmp");
		final var written = new java.util.concurrent.atomic.AtomicInteger();
		try (var out = Files.newOutputStream(tmp); var generator = JSON_FACTORY.createGenerator(out)) {
			generator.setCodec(MAPPER);
			generator.writeStartObject();
			generator.writeStringField("type", "FeatureCollection");
			generator.writeArrayFieldStart("features");
			jdbcTemplate.query(con -> {
				// PostgreSQL cursor streaming requires autocommit=off + an
				// explicit fetch size. Spring's JdbcTemplate wraps the
				// connection in a transaction when the query runs inside
				// query(PreparedStatementCreator, RowCallbackHandler), which
				// is enough to disable autocommit here.
				final var ps = con.prepareStatement(
						"SELECT geographic_code, name, geometry FROM geo_boundaries WHERE geographic_level = 'IRIS'",
						java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
				ps.setFetchSize(500);
				return ps;
			}, rs -> {
				try {
					final var code = rs.getString(1);
					final var name = rs.getString(2);
					final var geometryJson = rs.getString(3);
					if (code == null || geometryJson == null || geometryJson.isBlank()) {
						return;
					}
					generator.writeStartObject();
					generator.writeStringField("type", "Feature");
					generator.writeFieldName("geometry");
					generator.writeRawValue(geometryJson);
					generator.writeObjectFieldStart("properties");
					generator.writeStringField("code", code);
					generator.writeStringField("name", name != null ? name : code);
					// commune_code = the 5-char INSEE prefix the click handler
					// uses to navigate to /cities/{code}.
					generator.writeStringField("commune_code", code.length() >= 5 ? code.substring(0, 5) : code);
					generator.writeEndObject();
					generator.writeEndObject();
					written.incrementAndGet();
				} catch (IOException e) {
					throw new java.io.UncheckedIOException(e);
				}
			});
			generator.writeEndArray();
			generator.writeEndObject();
			generator.flush();
		} catch (java.io.UncheckedIOException e) {
			throw e.getCause();
		}
		if (written.get() == 0) {
			log.info("no IRIS rows in geo_boundaries — skipping iris layer in this rebuild");
			Files.deleteIfExists(tmp);
			return false;
		}
		Files.move(tmp, source, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		log.info("iris source written: {} features → {}", written.get(), source);
		return true;
	}

	private void fetchGeoApiCollection(URI url, Path destination) throws IOException, InterruptedException {
		final var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
		final var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(90)).GET().build();
		final var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			throw new IOException(url + " returned " + response.statusCode());
		}
		final var tmp = destination.resolveSibling(destination.getFileName() + ".tmp");
		try (var body = response.body(); var out = Files.newOutputStream(tmp)) {
			body.transferTo(out);
		}
		// geo.api.gouv.fr emits {nom: "Île-de-France"} ; rename to {name: ...}
		// so the frontend can share the same styleFor closure across all
		// three layers without per-layer key gymnastics.
		try (var in = Files.newInputStream(tmp); var parser = JSON_FACTORY.createParser(in)) {
			parser.setCodec(MAPPER);
			final var fc = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(parser);
			final var features = (com.fasterxml.jackson.databind.node.ArrayNode) fc.get("features");
			for (var node : features) {
				final var props = (com.fasterxml.jackson.databind.node.ObjectNode) node.get("properties");
				if (props != null && props.has("nom") && !props.has("name")) {
					props.set("name", props.get("nom"));
				}
			}
			Files.writeString(tmp, MAPPER.writeValueAsString(fc), StandardCharsets.UTF_8);
		}
		Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		log.info("fetched {} → {}", url, destination);
	}

	private void ensurePolygonSource(Path source) throws IOException, InterruptedException {
		if (Files.exists(source)) {
			return;
		}
		log.info("polygons source missing at {}, fetching from {}", source, geoApiBaseUrl);
		Files.createDirectories(source.getParent());

		final var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
		final var tmp = source.resolveSibling("communes-fr.geojson.tmp");
		try (var out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8);
				var generator = JSON_FACTORY.createGenerator(out)) {
			generator.setCodec(MAPPER);
			generator.writeStartObject();
			generator.writeStringField("type", "FeatureCollection");
			generator.writeArrayFieldStart("features");

			var totalFeatures = 0;
			for (final var dept : DEPARTMENTS) {
				final var url = URI.create(geoApiBaseUrl + "/departements/" + dept
						+ "/communes?fields=nom,code,population,surface&format=geojson&geometry=contour");
				final var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(60)).GET().build();
				final var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
				if (response.statusCode() != 200) {
					throw new IOException("geo.api.gouv.fr returned " + response.statusCode() + " for " + dept);
				}
				final var skipParentCode = ARRONDISSEMENT_PARENTS.get(dept);
				try (var body = response.body();
						var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
						var parser = JSON_FACTORY.createParser(reader)) {
					parser.setCodec(MAPPER);
					expect(parser.nextToken(), JsonToken.START_OBJECT);
					while (parser.nextToken() != JsonToken.END_OBJECT) {
						final var field = parser.currentName();
						parser.nextToken();
						if ("features".equals(field)) {
							expect(parser.currentToken(), JsonToken.START_ARRAY);
							while (parser.nextToken() != JsonToken.END_ARRAY) {
								// Copy each feature node straight through. Avoids
								// allocating a Map per commune.
								final var tree = parser.readValueAsTree();
								// Drop the parent commune of Paris/Lyon/Marseille
								// — its arrondissements are added below with
								// proper per-district codes (75101..). Without
								// this, the tile would draw a giant overlapping
								// polygon on top of the arrondissements and the
								// click target would always resolve to the parent.
								if (skipParentCode != null) {
									final var node = (com.fasterxml.jackson.databind.JsonNode) tree;
									final var code = node.path("properties").path("code").asText("");
									if (skipParentCode.equals(code)) {
										continue;
									}
								}
								generator.writeTree(tree);
								totalFeatures++;
							}
						} else {
							parser.skipChildren();
						}
					}
				}
				log.info("fetched polygons for dept {}", dept);

				if (skipParentCode != null) {
					totalFeatures += fetchArrondissements(http, generator, skipParentCode);
				}
			}

			generator.writeEndArray();
			generator.writeEndObject();
			generator.flush();
			log.info("polygons source fetched: {} features", totalFeatures);
		}
		Files.move(tmp, source, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/**
	 * Pull the arrondissement polygons for one of the three drilldown parent
	 * communes (Paris/Lyon/Marseille) and stream them into the in-flight
	 * FeatureCollection generator. Each arrondissement keeps its own INSEE code
	 * (75101..75120 etc.) so the frontend's click handler routes to the correct
	 * city page rather than the parent.
	 *
	 * <p>
	 * geo.api.gouv.fr dropped the
	 * {@code /communes/{code}/arrondissements-municipaux} route ; the supported
	 * pattern is now {@code /communes} filtered by
	 * {@code type=arrondissement-municipal} on the department.
	 */
	private int fetchArrondissements(HttpClient http, com.fasterxml.jackson.core.JsonGenerator generator,
			String parentCode) throws IOException, InterruptedException {
		final var dept = parentCode.substring(0, 2);
		final var url = URI.create(geoApiBaseUrl + "/communes?codeDepartement=" + dept
				+ "&type=arrondissement-municipal&fields=nom,code,population,surface&format=geojson&geometry=contour");
		final var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(60)).GET().build();
		final var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			log.warn("arrondissement fetch for {} returned {} — keeping parent commune polygon", parentCode,
					response.statusCode());
			return 0;
		}
		var count = 0;
		try (var body = response.body();
				var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
				var parser = JSON_FACTORY.createParser(reader)) {
			parser.setCodec(MAPPER);
			expect(parser.nextToken(), JsonToken.START_OBJECT);
			while (parser.nextToken() != JsonToken.END_OBJECT) {
				final var field = parser.currentName();
				parser.nextToken();
				if ("features".equals(field)) {
					expect(parser.currentToken(), JsonToken.START_ARRAY);
					while (parser.nextToken() != JsonToken.END_ARRAY) {
						generator.writeTree(parser.readValueAsTree());
						count++;
					}
				} else {
					parser.skipChildren();
				}
			}
		}
		log.info("fetched {} arrondissements for parent {}", count, parentCode);
		return count;
	}

	/**
	 * Bake the available layers into a single mbtiles using tippecanoe's per-layer
	 * JSON spec. Each level only appears at its useful zoom band — regions at
	 * z=4..6, departments at z=7..8, communes (+ arrondissements for
	 * Paris/Lyon/Marseille) at z=9..14, IRIS (~50 k Filosofi neighbourhood
	 * polygons) at z=13..14 when the source is available. Disjoint bands so the
	 * browser never downloads two layers competing for the same hover target.
	 *
	 * <p>
	 * IRIS is layered ABOVE cities at z=13..14: at that zoom level the user sees a
	 * single commune at most, so swapping the commune polygon for its IRIS
	 * subdivision is more useful than keeping the parent outline.
	 */
	private void runTippecanoe(Path regionsSrc, Path departmentsSrc, Path communesSrc, Path irisSrc, Path destination)
			throws IOException, InterruptedException {
		// Per-layer JSON keeps each level under its own simplification curve
		// so regions stay readable past z=4 while communes don't dissolve
		// before z=12. `extend-zooms-if-still-dropping` lets tippecanoe push
		// past --maximum-zoom on dense city blocks if dropping would lose
		// data — important for Paris arrondissements and IRIS detail.
		final var args = new java.util.ArrayList<String>();
		args.add(tippecanoeBin);
		args.add("--output=" + destination);
		args.add("--force");
		args.add("--minimum-zoom=4");
		args.add("--maximum-zoom=14");
		args.add("--drop-densest-as-needed");
		args.add("--extend-zooms-if-still-dropping");
		args.add("--no-tile-size-limit");
		args.add("-L");
		args.add("{\"layer\":\"regions\",\"file\":\"" + regionsSrc
				+ "\",\"minzoom\":4,\"maxzoom\":6,\"simplification\":12}");
		args.add("-L");
		args.add("{\"layer\":\"departments\",\"file\":\"" + departmentsSrc
				+ "\",\"minzoom\":7,\"maxzoom\":8,\"simplification\":10}");
		args.add("-L");
		args.add("{\"layer\":\"cities\",\"file\":\"" + communesSrc
				+ "\",\"minzoom\":9,\"maxzoom\":14,\"simplification\":8}");
		if (irisSrc != null) {
			args.add("-L");
			args.add("{\"layer\":\"iris\",\"file\":\"" + irisSrc
					+ "\",\"minzoom\":13,\"maxzoom\":14,\"simplification\":6}");
		}
		final var pb = new ProcessBuilder(args);
		pb.redirectErrorStream(true);
		final var process = pb.start();
		try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				log.info("tippecanoe: {}", line);
			}
		}
		final var exit = process.waitFor();
		if (exit != 0) {
			throw new IOException("tippecanoe exited " + exit);
		}
	}

	private static List<String> buildDepartmentCodes() {
		final var codes = new java.util.ArrayList<String>();
		for (int i = 1; i <= 95; i++) {
			if (i == 20) {
				codes.add("2A");
				codes.add("2B");
				continue;
			}
			codes.add(String.format("%02d", i));
		}
		codes.add("971");
		codes.add("972");
		codes.add("973");
		codes.add("974");
		codes.add("976");
		return List.copyOf(codes);
	}
}
