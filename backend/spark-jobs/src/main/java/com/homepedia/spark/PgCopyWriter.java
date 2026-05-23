package com.homepedia.spark;

import java.io.StringReader;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.postgresql.PGConnection;

/**
 * Bulk-load a Spark {@code Dataset<Row>} into a Postgres table using
 * {@code COPY FROM STDIN} instead of the default JDBC batch {@code INSERT}. On
 * the comparable-sales workload (~50 M rows), COPY cuts the write phase from ~8
 * minutes to ~50 seconds — Postgres skips the per-row parser, plan and WAL
 * bookkeeping that batched INSERT pays even with rewriteBatchedInserts.
 *
 * <p>
 * Layout:
 * <ol>
 * <li>Driver-side {@code TRUNCATE} (COPY is append-only — we wipe upstream to
 * keep {@link org.apache.spark.sql.SaveMode#Overwrite} semantics).</li>
 * <li>{@code foreachPartition}: each Spark task opens its own JDBC connection,
 * buffers the partition as CSV in memory, then hands the whole buffer to
 * {@code CopyManager.copyIn(Reader)}.</li>
 * </ol>
 *
 * <p>
 * Buffer sizing: at ~125k rows × 80 bytes per partition (50 M rows over 400
 * shuffle partitions), the StringBuilder peaks around 10 MB — smaller than a
 * single Spark task's working memory, no risk of pressuring the JVM heap.
 * Streamed PipedInputStream would be lower memory but adds a second thread per
 * task and trades complexity for gains we don't need at this scale.
 *
 * <p>
 * Limitations: only handles numeric / string / decimal / timestamp columns.
 * Strings get CSV-quoted (double quotes doubled). Nulls become empty fields —
 * matches Postgres' default {@code NULL ''} treatment in CSV format.
 */
public final class PgCopyWriter {

	private PgCopyWriter() {
	}

	public static void writeOverwrite(Dataset<Row> dataset, String jdbcUrl, String jdbcUser, String jdbcPassword,
			String tableName, String columnList) {
		writeOverwrite(dataset, jdbcUrl, jdbcUser, jdbcPassword, tableName, columnList, java.util.List.of());
	}

	/**
	 * Same as
	 * {@link #writeOverwrite(Dataset, String, String, String, String, String)} but
	 * also DROPs the supplied indexes before the COPY and re-CREATEs them after.
	 * Postgres maintains every index synchronously during a COPY — for
	 * {@code comparable_transactions} (50 M rows, PK + one secondary index) that's
	 * roughly half the wall-clock time. Building the index in one shot at the end
	 * uses bulk sort + bottom-up B-tree construction which is 2-3× faster than
	 * incremental updates.
	 *
	 * <p>
	 * Each entry in {@code indexes} is a (name, DDL) pair: {@code name} so we can
	 * {@code DROP INDEX IF EXISTS}, {@code DDL} so we can recreate exactly the same
	 * shape afterward. Caller passes the same DDL string Liquibase would have
	 * generated.
	 */
	public static void writeOverwrite(Dataset<Row> dataset, String jdbcUrl, String jdbcUser, String jdbcPassword,
			String tableName, String columnList, java.util.List<IndexSpec> indexes) {
		dropIndexes(jdbcUrl, jdbcUser, jdbcPassword, indexes);
		truncate(jdbcUrl, jdbcUser, jdbcPassword, tableName);
		// Capture locals so the lambda doesn't try to serialize this class.
		final var url = jdbcUrl;
		final var user = jdbcUser;
		final var pwd = jdbcPassword;
		final var table = tableName;
		final var cols = columnList;
		dataset.foreachPartition((java.util.Iterator<Row> rows) -> {
			if (!rows.hasNext()) {
				return;
			}
			final var sb = new StringBuilder(8 * 1024 * 1024);
			while (rows.hasNext()) {
				appendRowAsCsv(rows.next(), sb);
			}
			try (var conn = DriverManager.getConnection(url, user, pwd)) {
				final var pgConn = conn.unwrap(PGConnection.class);
				final var copy = pgConn.getCopyAPI();
				copy.copyIn("COPY " + table + " (" + cols + ") FROM STDIN (FORMAT csv)",
						new StringReader(sb.toString()));
			}
		});
		recreateIndexes(jdbcUrl, jdbcUser, jdbcPassword, indexes);
	}

	/**
	 * Drop + create DDL pair for an index or constraint. Both strings are provided
	 * explicitly so the caller can distinguish a plain index (DROP INDEX) from a PK
	 * (DROP CONSTRAINT) without us having to query pg_catalog at runtime.
	 */
	public record IndexSpec(String dropDdl, String createDdl) {
	}

	private static void dropIndexes(String url, String user, String pwd, java.util.List<IndexSpec> indexes) {
		if (indexes.isEmpty()) {
			return;
		}
		try (var conn = DriverManager.getConnection(url, user, pwd); var stmt = conn.createStatement()) {
			for (final var idx : indexes) {
				// Caller pre-baked IF EXISTS so a fresh table doesn't blow up.
				stmt.execute(idx.dropDdl());
			}
		} catch (SQLException e) {
			throw new RuntimeException("dropping indexes failed", e);
		}
	}

	private static void recreateIndexes(String url, String user, String pwd, java.util.List<IndexSpec> indexes) {
		if (indexes.isEmpty()) {
			return;
		}
		try (var conn = DriverManager.getConnection(url, user, pwd); var stmt = conn.createStatement()) {
			for (final var idx : indexes) {
				stmt.execute(idx.createDdl());
			}
		} catch (SQLException e) {
			throw new RuntimeException("recreating indexes failed", e);
		}
	}

	private static void truncate(String url, String user, String pwd, String table) {
		try (var conn = DriverManager.getConnection(url, user, pwd); var stmt = conn.createStatement()) {
			stmt.execute("TRUNCATE TABLE " + table);
		} catch (SQLException e) {
			throw new RuntimeException("truncate of " + table + " failed", e);
		}
	}

	private static void appendRowAsCsv(Row r, StringBuilder sb) {
		for (int i = 0; i < r.length(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			final var v = r.get(i);
			if (v == null) {
				// Empty field → NULL under FORMAT csv default.
				continue;
			}
			if (v instanceof String s) {
				// CSV-quote: wrap in double quotes, double any embedded quote.
				sb.append('"').append(s.replace("\"", "\"\"")).append('"');
			} else if (v instanceof java.sql.Timestamp t) {
				// Postgres parses ISO 8601 timestamps natively; keep the
				// millisecond precision Spark gives us.
				sb.append(t.toLocalDateTime());
			} else {
				sb.append(v);
			}
		}
		sb.append('\n');
	}
}
