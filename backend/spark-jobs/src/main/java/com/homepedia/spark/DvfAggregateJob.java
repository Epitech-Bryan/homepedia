package com.homepedia.spark;

import java.util.Properties;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

public final class DvfAggregateJob {

	private DvfAggregateJob() {
	}

	private record Config(String inputPath, String jdbcUrl, String jdbcUser, String jdbcPassword, String outputTable) {
		boolean readsFromJdbc() {
			return inputPath == null || inputPath.isBlank();
		}
	}

	public static void main(String[] args) {
		final var cfg = parseArgs(args);

		try (final var spark = SparkSession.builder().appName("homepedia-dvf-aggregate")
				// AQE + shuffle defaults — same reasoning as the comparable-
				// sales job: coalesce small partitions, skew-handle dense
				// departments. The aggregation here doesn't have a self-join
				// but the JDBC write still goes through a shuffle when we
				// repartition by department; AQE keeps the partition count
				// sensible for our cluster size.
				.config("spark.sql.adaptive.enabled", "true")
				.config("spark.sql.adaptive.coalescePartitions.enabled", "true")
				.config("spark.sql.shuffle.partitions", "64")
				// Cast Java records / Lombok types via Kryo — about 2x faster
				// than the default Java serializer for the rows shuffled by
				// the avg / percentile_approx aggregations.
				.config("spark.serializer", "org.apache.spark.serializer.KryoSerializer").getOrCreate()) {
			final var jdbcProps = new Properties();
			jdbcProps.put("user", cfg.jdbcUser());
			jdbcProps.put("password", cfg.jdbcPassword());
			jdbcProps.put("driver", "org.postgresql.Driver");

			final var dvf = cfg.readsFromJdbc()
					? loadDvfFromJdbc(spark, cfg.jdbcUrl(), jdbcProps)
					: loadDvf(spark, cfg.inputPath());
			// Broadcast the tiny cities dimension (~35 k rows × 2 ints =
			// ~350 KB) so the join becomes a hash-broadcast instead of a
			// shuffle join — easily the biggest gain on the previous
			// implementation since the DVF side has tens of millions of rows
			// and shuffling all of them by insee_code wastes a full minute.
			final var cities = loadCitiesMapping(spark, cfg.jdbcUrl(), jdbcProps);
			final var enriched = joinAndEnrich(dvf, functions.broadcast(cities));
			final var aggregated = aggregateByDepartment(enriched);

			// Coalesce to a single partition for the JDBC write — the output
			// is ~100 rows (one per dept), opening one connection is enough
			// and the previous 200-partition fan-out triggered N connection
			// open/close cycles for almost no payload each.
			// truncate=true makes Overwrite TRUNCATE the existing table instead of
			// DROP+CREATE, so the Liquibase-defined column types (double precision)
			// are preserved. Without it Spark recreates the table with its own
			// inferred types (numeric) and the rest-api's Hibernate schema
			// validation then crash-loops on the type mismatch.
			aggregated.coalesce(1).write().option("truncate", "true").mode(SaveMode.Overwrite).jdbc(cfg.jdbcUrl(),
					cfg.outputTable(), jdbcProps);
		}
	}

	private static Dataset<Row> loadDvf(SparkSession spark, String inputPath) {
		// Parquet fast path: when fed a directory staged by
		// DvfParquetStagingJob (any path that isn't a *.csv/*.csv.gz file),
		// read columnar Parquet — only the projected columns are scanned and
		// partition pruning skips untouched millésimes. Falls back to the CSV
		// reader for a raw geo-dvf dump.
		final var lower = inputPath.toLowerCase();
		final var isCsv = lower.endsWith(".csv") || lower.endsWith(".csv.gz") || lower.endsWith(".gz");
		final var raw = isCsv
				? spark.read().option("header", "true").option("inferSchema", "false").option("multiLine", "false")
						.csv(inputPath).select(functions.col("code_commune").alias("insee_code"),
								functions.col("valeur_fonciere").cast(DataTypes.DoubleType).alias("price"),
								functions.col("surface_reelle_bati").cast(DataTypes.DoubleType).alias("surface"),
								functions.col("date_mutation").alias("date"))
				: spark.read().parquet(inputPath).select(functions.col("insee_code"), functions.col("price"),
						functions.col("surface"), functions.col("date"));
		// Push the price filter down before the join so we never shuffle
		// null/zero-price rows we'd just drop after.
		return raw.filter(functions.col("price").isNotNull().and(functions.col("price").gt(0)));
	}

	private static Dataset<Row> loadDvfFromJdbc(SparkSession spark, String jdbcUrl, Properties jdbcProps) {
		final var query = """
				(SELECT city_insee_code AS insee_code, property_value AS price,
				        built_surface AS surface, mutation_date AS date, id
				 FROM transactions
				 WHERE property_value IS NOT NULL AND property_value > 0
				   AND city_insee_code IS NOT NULL) t
				""";
		return spark.read().option("partitionColumn", "id").option("lowerBound", "1").option("upperBound", "1000000000")
				.option("numPartitions", "16").option("fetchsize", "10000").jdbc(jdbcUrl, query, jdbcProps)
				.select(functions.col("insee_code"), functions.col("price"), functions.col("surface"),
						functions.col("date"));
	}

	private static Dataset<Row> loadCitiesMapping(SparkSession spark, String jdbcUrl, Properties jdbcProps) {
		return spark.read().jdbc(jdbcUrl, "(SELECT insee_code, department_code FROM cities) c", jdbcProps);
	}

	private static Dataset<Row> joinAndEnrich(Dataset<Row> dvf, Dataset<Row> cities) {
		// Price filter moved into loadDvfCsv so it runs before the join —
		// keeps this method to the column derivation it actually owns.
		return dvf.join(cities, "insee_code")
				.withColumn("price_per_sqm", functions
						.when(functions.col("surface").gt(0), functions.col("price").divide(functions.col("surface")))
						.otherwise(null));
	}

	private static Dataset<Row> aggregateByDepartment(Dataset<Row> enriched) {
		return enriched.groupBy("department_code").agg(functions.count("*").alias("transaction_count"),
				functions.avg("price").alias("avg_price"), functions.avg("price_per_sqm").alias("avg_price_per_sqm"),
				functions.expr("percentile_approx(price, 0.5)").alias("median_price"));
	}

	private static Config parseArgs(String[] args) {
		String inputPath = null;
		String jdbcUrl = null;
		String jdbcUser = "homepedia";
		String jdbcPassword = "homepedia";
		String outputTable = "dept_dvf_stats";

		for (int i = 0; i < args.length - 1; i++) {
			switch (args[i]) {
				case "--input-path" -> inputPath = args[++i];
				case "--jdbc-url" -> jdbcUrl = args[++i];
				case "--jdbc-user" -> jdbcUser = args[++i];
				case "--jdbc-password" -> jdbcPassword = args[++i];
				case "--output-table" -> outputTable = args[++i];
				default -> {
				}
			}
		}
		if (jdbcUrl == null) {
			throw new IllegalArgumentException(
					"Required: --jdbc-url (--input-path optional; omit to read the transactions table)");
		}
		return new Config(inputPath, jdbcUrl, jdbcUser, jdbcPassword, outputTable);
	}
}
