package com.homepedia.spark;

import java.util.Properties;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/**
 * Spark job that aggregates DVF transactions per department: total count, mean
 * price, mean €/m². Reads the raw DVF CSV (data.gouv.fr format) from
 * {@code --input-path}, joins with the cities table from PostgreSQL to attach
 * department codes, then writes the aggregated dataset to PostgreSQL table
 * {@code dept_dvf_stats} (overwrite mode).
 *
 * <p>This is the "Big Data" path of the project: it lets us reprocess the
 * full DVF (~10M rows, ~3 GB uncompressed) in parallel via a Spark cluster
 * (master + workers) instead of loading it row-by-row through Hibernate.
 *
 * <p>Submit with:
 * <pre>
 * spark-submit \
 *   --class com.homepedia.spark.DvfAggregateJob \
 *   --master spark://spark-master:7077 \
 *   --jars /opt/spark/jars/postgresql.jar \
 *   spark-jobs-3.11.1-spark.jar \
 *   --input-path /data/dvf.csv \
 *   --jdbc-url jdbc:postgresql://db:5432/homepedia \
 *   --jdbc-user homepedia --jdbc-password homepedia
 * </pre>
 */
public final class DvfAggregateJob {

	private DvfAggregateJob() {
	}

	public static void main(String[] args) {
		final var cfg = parseArgs(args);

		try (final var spark = SparkSession.builder().appName("homepedia-dvf-aggregate").getOrCreate()) {
			final var jdbcProps = new Properties();
			jdbcProps.put("user", cfg.jdbcUser);
			jdbcProps.put("password", cfg.jdbcPassword);
			jdbcProps.put("driver", "org.postgresql.Driver");

			// 1. Load DVF CSV (header line provided by data.gouv 'full' export).
			Dataset<Row> dvf = spark.read().option("header", "true").option("inferSchema", "false").csv(cfg.inputPath)
					.select(functions.col("code_commune").alias("insee_code"),
							functions.col("valeur_fonciere").cast(DataTypes.DoubleType).alias("price"),
							functions.col("surface_reelle_bati").cast(DataTypes.DoubleType).alias("surface"),
							functions.col("date_mutation").alias("date"));

			// 2. Load cities table (only insee_code → department_code mapping).
			Dataset<Row> cities = spark.read().jdbc(cfg.jdbcUrl, "(SELECT insee_code, department_code FROM cities) c",
					jdbcProps);

			// 3. Join, drop rows without surface, compute €/m².
			Dataset<Row> joined = dvf.join(cities, "insee_code")
					.filter(functions.col("price").isNotNull().and(functions.col("price").gt(0)))
					.withColumn("price_per_sqm",
							functions.when(functions.col("surface").gt(0), functions.col("price").divide(functions.col("surface")))
									.otherwise(null));

			// 4. Aggregate per department.
			Dataset<Row> aggregated = joined.groupBy("department_code").agg(
					functions.count("*").alias("transaction_count"), functions.avg("price").alias("avg_price"),
					functions.avg("price_per_sqm").alias("avg_price_per_sqm"),
					functions.expr("percentile_approx(price, 0.5)").alias("median_price"));

			// 5. Write back to Postgres.
			aggregated.write().mode(SaveMode.Overwrite).jdbc(cfg.jdbcUrl, "dept_dvf_stats", jdbcProps);

			System.out.println("DVF aggregate job complete: " + aggregated.count() + " departments processed.");
		}
	}

	private static Config parseArgs(String[] args) {
		final var cfg = new Config();
		for (int i = 0; i < args.length - 1; i++) {
			switch (args[i]) {
				case "--input-path" -> cfg.inputPath = args[++i];
				case "--jdbc-url" -> cfg.jdbcUrl = args[++i];
				case "--jdbc-user" -> cfg.jdbcUser = args[++i];
				case "--jdbc-password" -> cfg.jdbcPassword = args[++i];
				default -> {
				}
			}
		}
		if (cfg.inputPath == null || cfg.jdbcUrl == null) {
			throw new IllegalArgumentException("Required: --input-path, --jdbc-url");
		}
		return cfg;
	}

	private static final class Config {
		String inputPath;
		String jdbcUrl;
		String jdbcUser = "homepedia";
		String jdbcPassword = "homepedia";
	}
}
