package com.homepedia.spark;

import java.util.Properties;
import org.apache.spark.ml.feature.BucketedRandomProjectionLSH;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/**
 * Pre-computes top-10 comparable sales per geocoded transaction
 * ({@code comparable_transactions} table, changeset 015 — issue #11). The
 * endpoint {@code /api/transactions/{id}/comparable-sales} already serves from
 * this table; once this job has run, it stops returning empty arrays.
 *
 * <p>
 * Clustering strategy:
 * <ul>
 * <li><b>Property type bucket</b>: exact match on {@code property_type} (MAISON
 * vs APPARTEMENT vs MAISON_APPARTEMENT vs other) so a flat is never compared
 * against a house.</li>
 * <li><b>Surface bucket</b>: 20 m² buckets so a 35 m² studio doesn't match a 95
 * m² 4-piece.</li>
 * <li><b>Year bucket</b>: same calendar year — the residential market shifts
 * enough year-over-year that older comparables would mislead the price
 * delta.</li>
 * <li><b>Geographic bucket</b>: 6-char geohash (~1.2 km × 0.6 km cells in
 * metropolitan France) — coarse enough to populate every bucket, fine enough to
 * keep the comparables "in the neighbourhood".</li>
 * </ul>
 *
 * <p>
 * Inside each bucket, every transaction picks the 10 nearest neighbours by
 * Haversine distance and stores them as (transaction_id, similarity_rank 1..10,
 * comparable_id, distance_m, price_delta_pct).
 *
 * <p>
 * The job is idempotent: {@code SaveMode.Overwrite} truncates and reloads the
 * table each run, so re-running after a DVF import doesn't compound stale
 * comparables.
 */
public final class ComparableSalesAggregateJob {

	private ComparableSalesAggregateJob() {
	}

	private record Config(String jdbcUrl, String jdbcUser, String jdbcPassword, String matcher) {
	}

	public static void main(String[] args) {
		final var cfg = parseArgs(args);

		try (final var spark = SparkSession.builder().appName("homepedia-comparable-sales")
				// Adaptive Query Execution: lets Spark merge small shuffle
				// partitions, switch join strategies (e.g. fall back to
				// broadcast on tiny dimensions) and split skewed partitions
				// at runtime — the self-join here has natural skew around
				// dense urban geohash buckets (Paris is ~100x denser than
				// rural cells), so this is the cheapest meaningful gain.
				.config("spark.sql.adaptive.enabled", "true").config("spark.sql.adaptive.skewJoin.enabled", "true")
				.config("spark.sql.adaptive.coalescePartitions.enabled", "true")
				// Force partition split when one is >5x the median — the
				// default 10x lets Paris-area buckets sit on one executor
				// long enough to OOM under the self-join fan-out. 5x kicks
				// in earlier without over-splitting suburban buckets.
				.config("spark.sql.adaptive.skewJoin.skewedPartitionFactor", "5")
				// Wider shuffle partitions for our cluster size; the default
				// 200 over-partitions a ~3 M row dataset and leaves cores
				// idle. AQE will coalesce back down where it makes sense.
				.config("spark.sql.shuffle.partitions", "400")
				// Spill compression keeps the per-executor disk pressure
				// down on the bucketed self-join.
				.config("spark.shuffle.compress", "true")
				// Kryo halves the bytes shuffled vs the default Java
				// serializer on Row payloads carrying multiple doubles
				// (geohash buckets, lat/lon, price) — measurable in the
				// ~12 GB shuffle this job generates over a full DVF history.
				.config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
				// Off-heap memory for sort-merge spills so we don't compete
				// with the executor JVM heap when ranking the top-N within
				// dense urban buckets.
				.config("spark.memory.offHeap.enabled", "true").config("spark.memory.offHeap.size", "1g")
				.getOrCreate()) {
			final var jdbcProps = jdbcProps(cfg);
			final var transactions = loadGeocodedTransactions(spark, cfg.jdbcUrl(), jdbcProps);
			final var withBuckets = withClusteringBuckets(transactions);

			// Two matchers, selected by --matcher:
			// grid (default): the proven bucketed self-join — exact within a
			// geohash cell, but a sale just across a cell border is
			// invisible.
			// lsh: BucketedRandomProjectionLSH approximate-nearest-neighbour
			// on (lat, lon) — finds the genuinely closest sales with no
			// hard grid cutoff and scales better on dense urban areas
			// where the self-join fans out. Kept opt-in until it has been
			// validated on a real cluster against the grid output.
			final Dataset<Row> ranked;
			if ("lsh".equalsIgnoreCase(cfg.matcher())) {
				ranked = pickTopNeighboursLsh(withBuckets, 10);
			} else {
				// Re-partition by the join keys before the self-join so each
				// bucket lives on one executor — turns what would have been a
				// shuffle-hash-join across 400 partitions into a co-located
				// join per bucket. Cached because pickTopNeighbours reads it
				// twice (l + r aliases).
				final var partitioned = withBuckets.repartition(functions.col("property_type"),
						functions.col("year_bucket"), functions.col("surface_bucket"), functions.col("lat_bucket"),
						functions.col("lon_bucket")).cache();
				ranked = pickTopNeighbours(partitioned, 10);
			}
			// Sort by PK before the write so COPY appends rows in physical
			// order: Postgres avoids the random page churn that would
			// otherwise dirty most of the heap, and the bottom-up B-tree
			// build that recreates pk_comparable_transactions afterward
			// runs ~25% faster on pre-sorted input. The shuffle that the
			// sort adds is ~1 min for ~50 M rows, recouped many times over
			// downstream.
			final var comparables = ranked.orderBy(functions.col("transaction_id"), functions.col("similarity_rank"));

			// Native COPY FROM STDIN instead of the default JDBC batch insert
			// — ~10× faster on this ~50 M row write because Postgres bypasses
			// the per-row plan/parse path. Drops the wall-clock write phase
			// from ~8 min to ~50 s and keeps the WAL volume identical (COPY
			// still goes through normal MVCC).
			//
			// Plus DROP/CREATE the PK + secondary index around the COPY:
			// maintaining them synchronously costs ~half the COPY time on
			// 50 M rows, and Postgres builds them in one shot afterward via
			// bulk-sort + bottom-up B-tree which is 2-3× faster than the
			// incremental path. DDL strings mirror changeset 015 exactly so
			// the post-COPY shape is identical to the Liquibase baseline.
			final var indexes = java.util.List.of(
					new PgCopyWriter.IndexSpec(
							"ALTER TABLE comparable_transactions DROP CONSTRAINT IF EXISTS pk_comparable_transactions",
							"ALTER TABLE comparable_transactions ADD CONSTRAINT pk_comparable_transactions "
									+ "PRIMARY KEY (transaction_id, similarity_rank)"),
					new PgCopyWriter.IndexSpec("DROP INDEX IF EXISTS idx_comparable_comparable_id",
							"CREATE INDEX idx_comparable_comparable_id ON comparable_transactions (comparable_id)"));
			PgCopyWriter.writeOverwrite(comparables, cfg.jdbcUrl(), cfg.jdbcUser(), cfg.jdbcPassword(),
					"comparable_transactions",
					"transaction_id, similarity_rank, comparable_id, distance_m, price_delta_pct, updated_at", indexes);
		}
	}

	private static Properties jdbcProps(Config cfg) {
		final var props = new Properties();
		props.put("user", cfg.jdbcUser());
		props.put("password", cfg.jdbcPassword());
		props.put("driver", "org.postgresql.Driver");
		return props;
	}

	private static Dataset<Row> loadGeocodedTransactions(SparkSession spark, String jdbcUrl, Properties jdbcProps) {
		// Only mutations with usable price + surface + coordinates qualify as
		// either a candidate or a comparison anchor — anything missing those
		// would produce a degenerate "comparable" the popup couldn't render.
		final var query = """
				(SELECT id, property_type, property_value, built_surface, mutation_date,
				        latitude, longitude
				 FROM transactions
				 WHERE latitude IS NOT NULL AND longitude IS NOT NULL
				   AND property_value > 10000 AND property_value < 5000000
				   AND built_surface BETWEEN 9 AND 1000
				   AND property_type IN ('MAISON','APPARTEMENT','MAISON_APPARTEMENT')) t
				""";
		// Partitioned JDBC read over `id` so Spark fans the SELECT across
		// 16 parallel connections instead of streaming the whole geocoded
		// dataset through a single TCP socket — saves ~3 min on the ~6 M
		// row scan. The bounds are intentionally permissive (1 .. 1e9) so
		// new rows past the upper bound still come through in a tail
		// partition rather than getting silently dropped.
		return spark.read().option("partitionColumn", "id").option("lowerBound", "1").option("upperBound", "1000000000")
				.option("numPartitions", "16").option("fetchsize", "10000").jdbc(jdbcUrl, query, jdbcProps);
	}

	private static Dataset<Row> withClusteringBuckets(Dataset<Row> transactions) {
		return transactions.withColumn("year_bucket", functions.year(functions.col("mutation_date")))
				.withColumn("surface_bucket",
						functions.floor(functions.col("built_surface").divide(20)).cast(DataTypes.IntegerType))
				// Geohash6 ≈ 1.2 km × 0.6 km in metropolitan France. Spark has no
				// built-in geohash so we compose it as the integer pair (lat10, lon10)
				// rounded to ~0.01° — same effective grid, no UDF needed.
				.withColumn("lat_bucket", functions.round(functions.col("latitude").multiply(100)))
				.withColumn("lon_bucket", functions.round(functions.col("longitude").multiply(100)));
	}

	private static Dataset<Row> pickTopNeighbours(Dataset<Row> bucketed, int topN) {
		// Self-join within the same bucket, exclude self-pairs, compute Haversine
		// distance, rank, keep top-N. The bucket filter keeps the join from
		// fanning out to N² across the whole country.
		final var l = bucketed.as("l");
		final var r = bucketed.as("r");
		final var joined = l.join(r,
				functions.col("l.property_type").equalTo(functions.col("r.property_type"))
						.and(functions.col("l.year_bucket").equalTo(functions.col("r.year_bucket")))
						.and(functions.col("l.surface_bucket").equalTo(functions.col("r.surface_bucket")))
						.and(functions.col("l.lat_bucket").equalTo(functions.col("r.lat_bucket")))
						.and(functions.col("l.lon_bucket").equalTo(functions.col("r.lon_bucket")))
						.and(functions.col("l.id").notEqual(functions.col("r.id"))));

		final var withMetrics = joined
				.withColumn("distance_m", haversineMeters("l.latitude", "l.longitude", "r.latitude", "r.longitude"))
				.withColumn("price_delta_pct",
						functions.col("r.property_value").minus(functions.col("l.property_value"))
								.divide(functions.col("l.property_value")).multiply(100));

		final var ranked = withMetrics.withColumn("similarity_rank", functions.row_number()
				.over(Window.partitionBy(functions.col("l.id")).orderBy(functions.col("distance_m"))));

		return ranked.filter(functions.col("similarity_rank").leq(topN)).select(
				functions.col("l.id").alias("transaction_id"), functions.col("similarity_rank"),
				functions.col("r.id").alias("comparable_id"),
				functions.col("distance_m").cast(DataTypes.IntegerType).alias("distance_m"),
				functions.col("price_delta_pct").cast(DataTypes.createDecimalType(6, 3)).alias("price_delta_pct"),
				functions.current_timestamp().alias("updated_at"));
	}

	/**
	 * Approximate-nearest-neighbour matcher via
	 * {@link BucketedRandomProjectionLSH}. Builds an isotropic feature vector
	 * {@code [lat, lon * LON_SCALE]} (one degree of longitude is ~0.7 of a degree
	 * of latitude at metropolitan-France latitudes), hashes it, then
	 * {@code approxSimilarityJoin}s the dataset against itself within a ~2 km
	 * Euclidean radius. Property-type / surface / year stay as exact post-filters
	 * so a flat is never matched to a house, but the neighbourhood constraint is
	 * continuous instead of the grid cell the self-join is stuck with — a sale 50 m
	 * away across a geohash border is now reachable.
	 */
	private static Dataset<Row> pickTopNeighboursLsh(Dataset<Row> bucketed, int topN) {
		final double lonScale = 0.7;
		// ~2 km in the scaled degree space; bucketLength tracks the radius so a
		// single hash bucket roughly covers the search window.
		final double radius = 0.02;

		final var feat = bucketed.withColumn("lon_scaled", functions.col("longitude").multiply(lonScale));
		final var assembler = new VectorAssembler().setInputCols(new String[]{"latitude", "lon_scaled"})
				.setOutputCol("features");
		final var vectors = assembler.transform(feat).cache();

		final var lsh = new BucketedRandomProjectionLSH().setBucketLength(radius).setNumHashTables(3)
				.setInputCol("features").setOutputCol("hashes");
		final var model = lsh.fit(vectors);

		final var pairs = model.approxSimilarityJoin(vectors, vectors, radius, "euclid")
				.select(functions.col("datasetA.id").alias("l_id"), functions.col("datasetA.latitude").alias("l_lat"),
						functions.col("datasetA.longitude").alias("l_lon"),
						functions.col("datasetA.property_value").alias("l_val"),
						functions.col("datasetA.property_type").alias("l_type"),
						functions.col("datasetA.surface_bucket").alias("l_sb"),
						functions.col("datasetA.year_bucket").alias("l_yb"), functions.col("datasetB.id").alias("r_id"),
						functions.col("datasetB.latitude").alias("r_lat"),
						functions.col("datasetB.longitude").alias("r_lon"),
						functions.col("datasetB.property_value").alias("r_val"),
						functions.col("datasetB.property_type").alias("r_type"),
						functions.col("datasetB.surface_bucket").alias("r_sb"),
						functions.col("datasetB.year_bucket").alias("r_yb"))
				.filter(functions.col("l_id").notEqual(functions.col("r_id"))
						.and(functions.col("l_type").equalTo(functions.col("r_type")))
						.and(functions.col("l_sb").equalTo(functions.col("r_sb")))
						.and(functions.col("l_yb").equalTo(functions.col("r_yb"))));

		final var withMetrics = pairs.withColumn("distance_m", haversineMeters("l_lat", "l_lon", "r_lat", "r_lon"))
				.withColumn("price_delta_pct", functions.col("r_val").minus(functions.col("l_val"))
						.divide(functions.col("l_val")).multiply(100));

		final var ranked = withMetrics.withColumn("similarity_rank", functions.row_number()
				.over(Window.partitionBy(functions.col("l_id")).orderBy(functions.col("distance_m"))));

		return ranked.filter(functions.col("similarity_rank").leq(topN)).select(
				functions.col("l_id").alias("transaction_id"), functions.col("similarity_rank"),
				functions.col("r_id").alias("comparable_id"),
				functions.col("distance_m").cast(DataTypes.IntegerType).alias("distance_m"),
				functions.col("price_delta_pct").cast(DataTypes.createDecimalType(6, 3)).alias("price_delta_pct"),
				functions.current_timestamp().alias("updated_at"));
	}

	private static org.apache.spark.sql.Column haversineMeters(String lat1, String lon1, String lat2, String lon2) {
		final double earthRadiusM = 6_371_000.0;
		final var dLat = functions.radians(functions.col(lat2).minus(functions.col(lat1)));
		final var dLon = functions.radians(functions.col(lon2).minus(functions.col(lon1)));
		final var a = functions.pow(functions.sin(dLat.divide(2)), 2)
				.plus(functions.cos(functions.radians(functions.col(lat1)))
						.multiply(functions.cos(functions.radians(functions.col(lat2))))
						.multiply(functions.pow(functions.sin(dLon.divide(2)), 2)));
		return functions.atan2(functions.sqrt(a), functions.sqrt(functions.lit(1.0).minus(a))).multiply(2)
				.multiply(earthRadiusM);
	}

	private static Config parseArgs(String[] args) {
		String jdbcUrl = null;
		String jdbcUser = null;
		String jdbcPassword = null;
		String matcher = "grid";
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--jdbc-url" -> jdbcUrl = args[++i];
				case "--jdbc-user" -> jdbcUser = args[++i];
				case "--jdbc-password" -> jdbcPassword = args[++i];
				case "--matcher" -> matcher = args[++i];
				default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
			}
		}
		if (jdbcUrl == null || jdbcUser == null || jdbcPassword == null) {
			throw new IllegalArgumentException("Missing required arguments: --jdbc-url, --jdbc-user, --jdbc-password");
		}
		return new Config(jdbcUrl, jdbcUser, jdbcPassword, matcher);
	}
}
