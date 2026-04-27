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

	private record Config(String inputPath, String jdbcUrl, String jdbcUser, String jdbcPassword) {
	}

	public static void main(String[] args) {
		final var cfg = parseArgs(args);

		try (final var spark = SparkSession.builder().appName("homepedia-dvf-aggregate").getOrCreate()) {
			final var jdbcProps = new Properties();
			jdbcProps.put("user", cfg.jdbcUser());
			jdbcProps.put("password", cfg.jdbcPassword());
			jdbcProps.put("driver", "org.postgresql.Driver");

			final var dvf = loadDvfCsv(spark, cfg.inputPath());
			final var cities = loadCitiesMapping(spark, cfg.jdbcUrl(), jdbcProps);
			final var enriched = joinAndEnrich(dvf, cities);
			final var aggregated = aggregateByDepartment(enriched);

			aggregated.write().mode(SaveMode.Overwrite).jdbc(cfg.jdbcUrl(), "dept_dvf_stats", jdbcProps);
		}
	}

	private static Dataset<Row> loadDvfCsv(SparkSession spark, String inputPath) {
		return spark.read().option("header", "true").option("inferSchema", "false").csv(inputPath).select(
				functions.col("code_commune").alias("insee_code"),
				functions.col("valeur_fonciere").cast(DataTypes.DoubleType).alias("price"),
				functions.col("surface_reelle_bati").cast(DataTypes.DoubleType).alias("surface"),
				functions.col("date_mutation").alias("date"));
	}

	private static Dataset<Row> loadCitiesMapping(SparkSession spark, String jdbcUrl, Properties jdbcProps) {
		return spark.read().jdbc(jdbcUrl, "(SELECT insee_code, department_code FROM cities) c", jdbcProps);
	}

	private static Dataset<Row> joinAndEnrich(Dataset<Row> dvf, Dataset<Row> cities) {
		return dvf.join(cities, "insee_code")
				.filter(functions.col("price").isNotNull().and(functions.col("price").gt(0)))
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

		for (int i = 0; i < args.length - 1; i++) {
			switch (args[i]) {
				case "--input-path" -> inputPath = args[++i];
				case "--jdbc-url" -> jdbcUrl = args[++i];
				case "--jdbc-user" -> jdbcUser = args[++i];
				case "--jdbc-password" -> jdbcPassword = args[++i];
				default -> {
				}
			}
		}
		if (inputPath == null || jdbcUrl == null) {
			throw new IllegalArgumentException("Required: --input-path, --jdbc-url");
		}
		return new Config(inputPath, jdbcUrl, jdbcUser, jdbcPassword);
	}
}
