package com.homepedia.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.homepedia.api.TestcontainersConfig;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Sanity test that every Liquibase changeset applies cleanly against an empty
 * Postgres container — closes issue #1. Each {@code @SpringBootTest} startup
 * runs the full master changelog, so by the time this test's {@link DataSource}
 * is autowired the schema is built. The asserts below pin the post-migration
 * state so a future changeset that conflicts (renamed column, missing index,
 * duplicate id) fails CI loudly instead of silently shifting prod schema.
 *
 * <p>
 * Cheap, fast (~3 s once the PG container is warm). Cannot replace
 * per-changeset reviews, but catches a category of regressions that the
 * unit-mock tests never reach.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class LiquibaseIT {

	@Autowired
	private DataSource dataSource;

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	@Test
	void everyChangelogEntryIsMarkedExecuted() {
		final var count = jdbc().queryForObject("SELECT COUNT(*) FROM databasechangelog", Integer.class);
		// 16 application changesets (001..016, with 002 = Spring Batch
		// schema) + 017 autovacuum backfill + the Spring Batch ones that
		// ship inside changeset 002. We assert a floor, not an equality,
		// because adding a new changeset shouldn't break the test.
		assertThat(count).as("All declared changesets executed").isGreaterThanOrEqualTo(17);
	}

	@Test
	void allDeclaredTablesExist() {
		// Lock the core schema. Adding a table is fine, dropping one isn't —
		// the assertion catches an accidental rollback or a typo in a new
		// changeset.
		final var tables = jdbc().queryForList(
				"SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = 'public' ORDER BY tablename",
				String.class);
		assertThat(tables).contains("regions", "departments", "cities", "transactions", "indicators", "geo_boundaries",
				"city_reviews", "dept_dvf_stats", "admins", "city_dvf_yearly_stats", "city_price_quarterly_stats",
				"comparable_transactions");
	}

	@Test
	void transactionsIsPartitioned() {
		final var isPartitioned = jdbc().queryForObject(
				"SELECT relkind FROM pg_class WHERE relname = 'transactions' AND relnamespace = 'public'::regnamespace",
				String.class);
		assertThat(isPartitioned).as("transactions must be a partitioned table (relkind=p)").isEqualTo("p");
		final var childCount = jdbc().queryForObject(
				"SELECT COUNT(*) FROM pg_inherits WHERE inhparent = 'transactions'::regclass", Integer.class);
		// 2014..2030 + transactions_default → 18. Asserts a floor to keep
		// the test stable if we ever extend the range.
		assertThat(childCount).isGreaterThanOrEqualTo(18);
	}

	@Test
	void everyPartitionHasAutovacuumThresholdsApplied() {
		// changeset 017 should have run reloptions on every existing child.
		final var anyMissing = jdbc().query(
				"""
						SELECT relname, reloptions
						FROM pg_class
						WHERE relname LIKE 'transactions_%'
						  AND relkind IN ('r','p')
						  AND (reloptions IS NULL OR NOT array_to_string(reloptions, ',') LIKE '%autovacuum_vacuum_scale_factor=0.05%')
						""",
				(rs, i) -> rs.getString("relname"));
		assertThat(anyMissing).as("Every transactions_* partition must carry the autovacuum tuning").isEmpty();
	}

	@Test
	void expectedIndexesExist() {
		final var indexes = jdbc().queryForList(
				"SELECT indexname FROM pg_indexes WHERE schemaname = 'public' ORDER BY indexname", String.class);
		assertThat(indexes).contains("idx_cities_department_code", "idx_cities_name_trgm", "idx_transaction_city",
				"idx_transaction_date", "idx_transaction_type", "idx_indicator_geo", "idx_indicator_geo_category",
				"idx_indicator_iris_code_prefix", "idx_transaction_geocoded_bbox");
	}

	@Test
	void pgTrgmExtensionEnabled() {
		final Integer present = jdbc().queryForObject("SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_trgm'",
				Integer.class);
		assertThat(present).as("pg_trgm must be enabled (changeset 011 dependency)").isEqualTo(1);
	}

}
