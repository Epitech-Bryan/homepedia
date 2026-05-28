package com.homepedia.spark;

import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/**
 * Converts a raw geo-dvf {@code full.csv(.gz)} into columnar Parquet
 * partitioned by {@code year}, written once so the heavier aggregation jobs
 * stop re-parsing the ~3 GB text dump on every run.
 *
 * <p>
 * Why this is the cheapest speed win across the whole Spark side:
 * <ul>
 * <li>Parquet is columnar — {@link DvfAggregateJob} reads only the four columns
 * it aggregates, so the scan moves ~90% fewer bytes than the CSV path.</li>
 * <li>Predicate + partition pruning: a re-run for a single millésime touches
 * only {@code year=2025/} instead of the whole file.</li>
 * <li>Snappy-compressed and splittable, so executors parallelise the read
 * without the gzip single-stream bottleneck.</li>
 * </ul>
 *
 * <p>
 * Run once per import, then point {@link DvfAggregateJob} at the Parquet
 * directory instead of the CSV (it auto-detects: a path without a {@code .csv}
 * extension is read as Parquet).
 *
 * <pre>
 * spark-submit --class com.homepedia.spark.DvfParquetStagingJob job.jar \
 *   --input-path /data/dvf-2025.csv.gz --output-path /data/dvf-parquet
 * </pre>
 */
public final class DvfParquetStagingJob {

	private DvfParquetStagingJob() {
	}

	private record Config(String inputPath, String outputPath) {
	}

	public static void main(String[] args) {
		final var cfg = parseArgs(args);

		try (final var spark = SparkSession.builder().appName("homepedia-dvf-parquet-staging")
				.config("spark.sql.adaptive.enabled", "true")
				.config("spark.serializer", "org.apache.spark.serializer.KryoSerializer").getOrCreate()) {

			// Project + type the columns the downstream jobs need (the four
			// aggregation columns plus coordinates for the comparable/heatmap
			// jobs) and drop the ~40 raw columns before writing. inferSchema
			// off — explicit casts avoid the extra full-file scan.
			final var typed = spark.read().option("header", "true").option("inferSchema", "false")
					.option("multiLine", "false").csv(cfg.inputPath())
					.select(functions.col("code_commune").alias("insee_code"),
							functions.col("valeur_fonciere").cast(DataTypes.DoubleType).alias("price"),
							functions.col("surface_reelle_bati").cast(DataTypes.DoubleType).alias("surface"),
							functions.col("type_local").alias("type_local"),
							functions.col("longitude").cast(DataTypes.DoubleType).alias("longitude"),
							functions.col("latitude").cast(DataTypes.DoubleType).alias("latitude"),
							functions.col("date_mutation").alias("date"))
					.filter(functions.col("price").isNotNull().and(functions.col("price").gt(0)))
					.withColumn("year", functions.year(functions.col("date")));

			typed.write().mode(SaveMode.Overwrite).partitionBy("year").parquet(cfg.outputPath());
		}
	}

	private static Config parseArgs(String[] args) {
		String inputPath = null;
		String outputPath = null;
		for (int i = 0; i < args.length - 1; i++) {
			switch (args[i]) {
				case "--input-path" -> inputPath = args[++i];
				case "--output-path" -> outputPath = args[++i];
				default -> {
				}
			}
		}
		if (inputPath == null || outputPath == null) {
			throw new IllegalArgumentException("Required: --input-path, --output-path");
		}
		return new Config(inputPath, outputPath);
	}
}
