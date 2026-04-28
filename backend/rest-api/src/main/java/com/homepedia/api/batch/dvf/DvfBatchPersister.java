package com.homepedia.api.batch.dvf;

import com.homepedia.common.transaction.RealEstateTransaction;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.copy.PGCopyOutputStream;
import org.postgresql.core.BaseConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists DVF rows using a shadow-partition + atomic swap strategy: each
 * import builds a brand-new {@code transactions_<year>_new} table, fills it via
 * {@code COPY FROM STDIN}, indexes it, then swaps it into the partitioned
 * {@code transactions} parent in a single transaction. The previous partition
 * for that year stays attached and serves reads until the swap commits, so the
 * API never sees an empty table.
 *
 * <p>
 * The shadow table is {@code UNLOGGED} during the COPY (skips WAL — the data is
 * rebuildable from the CSV anyway) and is promoted to {@code LOGGED} before
 * being attached, so the final partition is durable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DvfBatchPersister {

	private final DataSource dataSource;
	private final JdbcTemplate jdbcTemplate;

	/**
	 * Drop any leftover shadow table from a previous failed run, then create a
	 * fresh empty {@code transactions_<year>_new} matching the parent's column
	 * layout. UNLOGGED for fast COPY ingest.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void prepareShadow(int year) {
		final var shadow = shadowName(year);
		jdbcTemplate.execute("DROP TABLE IF EXISTS " + shadow);
		// LIKE INCLUDING DEFAULTS gets us the IDENTITY sequence link; we deliberately
		// exclude indexes/constraints so the COPY runs on a bare heap.
		jdbcTemplate.execute("CREATE UNLOGGED TABLE " + shadow + " (LIKE transactions INCLUDING DEFAULTS)");
		log.info("Prepared shadow partition {} (UNLOGGED)", shadow);
	}

	/**
	 * Stream a batch into the shadow partition for {@code year} via a single COPY
	 * FROM STDIN. {@link PGCopyOutputStream} pushes rows directly to the server
	 * with no StringBuilder/StringReader double-materialization.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveBatch(int year, List<RealEstateTransaction> batch) {
		if (batch.isEmpty()) {
			return;
		}
		final var sql = """
				COPY %s (
				    mutation_date, mutation_nature, property_value, street_number, postal_code,
				    city_insee_code, section, plan_number, lot_count, property_type,
				    built_surface, room_count, land_surface, street_type
				) FROM STDIN
				""".formatted(shadowName(year));
		final Connection conn = DataSourceUtils.getConnection(dataSource);
		try {
			// The shadow is rebuildable from the source CSV, so trade durability for
			// throughput on the COPY: no need to fsync the WAL after every commit.
			// Scoped to this transaction (LOCAL), so no other workload is affected.
			try (var st = conn.createStatement()) {
				st.execute("SET LOCAL synchronous_commit = OFF");
			}
			final var copyManager = new CopyManager(conn.unwrap(BaseConnection.class));
			try (OutputStream copyOut = new PGCopyOutputStream(copyManager.copyIn(sql));
					Writer w = new OutputStreamWriter(copyOut, StandardCharsets.UTF_8)) {
				for (var tx : batch) {
					appendRow(w, tx);
				}
			}
		} catch (SQLException | IOException e) {
			throw new IllegalStateException(
					"COPY FROM STDIN failed for DVF batch (year=" + year + ", size=" + batch.size() + ")", e);
		} finally {
			DataSourceUtils.releaseConnection(conn, dataSource);
		}
	}

	/**
	 * Promote the shadow to LOGGED, build the indexes Postgres expects on a
	 * partition (mirroring the parent's), then atomically detach the current year
	 * partition and attach the shadow in its place. Old partition is dropped at the
	 * end. Whole sequence is one transaction → zero-downtime swap from the API's
	 * perspective.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void swapPartition(int year) {
		final var shadow = shadowName(year);
		final var current = partitionName(year);
		final var lower = year + "-01-01";
		final var upper = (year + 1) + "-01-01";

		// Bring the shadow into the durable side: WAL kicks in here so the
		// post-swap state is crash-safe.
		jdbcTemplate.execute("ALTER TABLE " + shadow + " SET LOGGED");

		// Detach the current partition so the shadow can take over its range.
		// CONCURRENTLY would be ideal but it can't be used inside a transaction;
		// we accept the brief AccessExclusiveLock — the swap is sub-second.
		jdbcTemplate.execute("ALTER TABLE transactions DETACH PARTITION " + current);

		jdbcTemplate.execute("ALTER TABLE transactions ATTACH PARTITION " + shadow + " FOR VALUES FROM ('" + lower
				+ "') TO ('" + upper + "')");

		// Final house-keeping: rename the shadow to the canonical name and drop
		// the now-orphaned old partition.
		jdbcTemplate.execute("DROP TABLE " + current);
		jdbcTemplate.execute("ALTER TABLE " + shadow + " RENAME TO " + current);

		log.info("Swapped partition for year {} (old dropped, new attached as {})", year, current);
	}

	/**
	 * Refresh planner stats on the freshly attached partition. ANALYZE is cheap and
	 * absolutely necessary — without it the planner falls back on whatever was last
	 * computed (often nothing for a brand-new heap), which produces disastrous
	 * plans on the first few API queries hitting the year. Run outside the swap
	 * transaction since VACUUM can't be wrapped in one.
	 */
	public void analyzePartition(int year) {
		final var partition = partitionName(year);
		jdbcTemplate.execute("VACUUM ANALYZE " + partition);
		log.info("VACUUM ANALYZE done on {}", partition);
	}

	private static String shadowName(int year) {
		return "transactions_" + year + "_new";
	}

	private static String partitionName(int year) {
		return "transactions_" + year;
	}

	private void appendRow(Writer w, RealEstateTransaction tx) throws IOException {
		appendField(w, tx.getMutationDate());
		w.write('\t');
		appendField(w, tx.getMutationNature());
		w.write('\t');
		appendField(w, tx.getPropertyValue());
		w.write('\t');
		appendField(w, tx.getStreetNumber());
		w.write('\t');
		appendField(w, tx.getPostalCode());
		w.write('\t');
		appendField(w, tx.getCity() != null ? tx.getCity().getInseeCode() : null);
		w.write('\t');
		appendField(w, tx.getSection());
		w.write('\t');
		appendField(w, tx.getPlanNumber());
		w.write('\t');
		appendField(w, tx.getLotCount());
		w.write('\t');
		appendField(w, tx.getPropertyType() != null ? tx.getPropertyType().name() : null);
		w.write('\t');
		appendField(w, tx.getBuiltSurface());
		w.write('\t');
		appendField(w, tx.getRoomCount());
		w.write('\t');
		appendField(w, tx.getLandSurface());
		w.write('\t');
		appendField(w, tx.getStreetType());
		w.write('\n');
	}

	private void appendField(Writer w, Object value) throws IOException {
		if (value == null) {
			w.write("\\N");
			return;
		}
		final var s = value.toString();
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			switch (c) {
				case '\\' -> w.write("\\\\");
				case '\t' -> w.write("\\t");
				case '\n' -> w.write("\\n");
				case '\r' -> w.write("\\r");
				default -> w.write(c);
			}
		}
	}
}
