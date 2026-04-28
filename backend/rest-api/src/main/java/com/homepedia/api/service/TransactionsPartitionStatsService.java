package com.homepedia.api.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Tiny read-only helper for the admin UI: returns the row count of each yearly
 * {@code transactions_<YYYY>} partition. Goes through {@code pg_class} +
 * {@code pg_inherits} rather than {@code COUNT(*)} per partition because the
 * latter would require N table scans on a multi-million-row dataset — the
 * planner stats are good enough for a "is the year populated?" panel.
 */
@Service
@RequiredArgsConstructor
public class TransactionsPartitionStatsService {

	private static final String SQL = """
			SELECT
			    SUBSTRING(c.relname FROM 'transactions_(\\d{4})')::int AS year,
			    c.reltuples::bigint                                    AS approx_count
			FROM   pg_inherits i
			JOIN   pg_class    c ON c.oid = i.inhrelid
			JOIN   pg_class    p ON p.oid = i.inhparent
			WHERE  p.relname = 'transactions'
			  AND  c.relname ~ '^transactions_\\d{4}$'
			ORDER BY year DESC
			""";

	private final JdbcTemplate jdbcTemplate;

	public List<YearCount> countByYear() {
		return jdbcTemplate.query(SQL,
				(rs, i) -> new YearCount(rs.getInt("year"), Math.max(0L, rs.getLong("approx_count"))));
	}

	public record YearCount(int year, long approxCount) {
	}
}
