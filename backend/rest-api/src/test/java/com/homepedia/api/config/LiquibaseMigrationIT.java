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
 * Validates that the full Liquibase changelog applies cleanly to an empty
 * PostgreSQL container. {@link SpringBootTest} already runs migrations as part
 * of context startup; this IT codifies the invariants we want to keep across
 * future changesets so the assertions are explicit and informative when one
 * breaks.
 *
 * <p>
 * Adding a new changeset that conflicts with the existing schema, drops a
 * referenced index, or breaks the partitioning of {@code transactions} will
 * fail here long before it gets near production.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class LiquibaseMigrationIT {

	@Autowired
	private DataSource dataSource;

	@Test
	void allChangesetsApplied() {
		final var jdbc = new JdbcTemplate(dataSource);
		final var executed = jdbc.queryForList("SELECT id FROM databasechangelog ORDER BY orderexecuted", String.class);
		// Changesets get appended every release; assert on a stable
		// floor rather than a hard count so adding new ones doesn't
		// fail this test.
		assertThat(executed).hasSizeGreaterThan(10).contains("001-create-regions", "005-partition-transactions-by-year",
				"008-city-dvf-yearly-stats", "010-transaction-coords", "011-enable-pg-trgm");
	}

	@Test
	void transactionsTable_isRangePartitionedByMutationDate() {
		final var jdbc = new JdbcTemplate(dataSource);
		final var partitionStrategy = jdbc.queryForObject(
				"SELECT pg_get_partkeydef(c.oid) FROM pg_class c WHERE c.relname = 'transactions'", String.class);
		assertThat(partitionStrategy)
				.as("transactions must stay RANGE-partitioned by mutation_date — the DVF importer's"
						+ " shadow-partition + atomic-swap pattern depends on it")
				.isEqualToIgnoringCase("RANGE (mutation_date)");
	}

	@Test
	void citySearchIndexes_areInPlace() {
		final var jdbc = new JdbcTemplate(dataSource);
		final var indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'cities'",
				String.class);
		// The trigram index keys the LIKE %query% search; the FK index
		// keys findByDepartmentCode. Both shipped in changeset 011 and
		// regressing either turns the city autocomplete and the
		// department listing into seq scans over 35k rows.
		assertThat(indexes).contains("idx_cities_department_code", "idx_cities_name_trgm");
	}

	@Test
	void trgmExtensionAvailable() {
		final var jdbc = new JdbcTemplate(dataSource);
		final var extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);
		assertThat(extensions).as("pg_trgm is a runtime dependency for the cities search index").contains("pg_trgm");
	}
}
