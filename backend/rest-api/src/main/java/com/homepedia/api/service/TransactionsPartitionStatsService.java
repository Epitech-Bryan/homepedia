package com.homepedia.api.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tiny read-only helper for the admin UI: returns the row count of each yearly
 * {@code transactions_<YYYY>} partition. Goes through {@code pg_class} +
 * {@code pg_inherits} rather than {@code COUNT(*)} per partition because the
 * latter would require N table scans on a multi-million-row dataset — the
 * planner stats are good enough for a "is the year populated?" panel.
 *
 * <p>
 * Each row is also enriched with the most recent successful
 * {@code dvfImportJob} run for that year (from Spring Batch metadata) so the
 * admin can see how long the last import took. Joins
 * {@code batch_job_execution} with {@code batch_job_execution_params} where
 * {@code parameter_name='year'}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionsPartitionStatsService {

	private static final String SQL = """
			SELECT
			    SUBSTRING(c.relname FROM 'transactions_(\\d{4})')::int AS year,
			    c.reltuples::bigint                                    AS approx_count,
			    last_run.start_time                                    AS last_run_at,
			    last_run.duration_ms                                   AS last_duration_ms
			FROM   pg_inherits i
			JOIN   pg_class    c ON c.oid = i.inhrelid
			JOIN   pg_class    p ON p.oid = i.inhparent
			LEFT JOIN LATERAL (
			    SELECT je.start_time,
			           (EXTRACT(EPOCH FROM (je.end_time - je.start_time)) * 1000)::bigint AS duration_ms
			    FROM   batch_job_execution        je
			    JOIN   batch_job_instance         ji ON ji.job_instance_id = je.job_instance_id
			    JOIN   batch_job_execution_params jp ON jp.job_execution_id = je.job_execution_id
			    WHERE  ji.job_name = 'dvfImportJob'
			      AND  jp.parameter_name = 'year'
			      AND  jp.parameter_value = SUBSTRING(c.relname FROM 'transactions_(\\d{4})')
			      AND  je.status = 'COMPLETED'
			      AND  je.end_time IS NOT NULL
			    ORDER BY je.start_time DESC
			    LIMIT 1
			) last_run ON TRUE
			WHERE  p.relname = 'transactions'
			  AND  c.relname ~ '^transactions_\\d{4}$'
			ORDER BY year DESC
			""";

	private final JdbcTemplate jdbcTemplate;
	private final JobExplorer jobExplorer;

	public List<YearCount> countByYear() {
		return jdbcTemplate.query(SQL, (rs, i) -> {
			final int year = rs.getInt("year");
			final long approxCount = Math.max(0L, rs.getLong("approx_count"));
			final long durationMs = rs.getLong("last_duration_ms");
			final Long lastDurationMs = rs.wasNull() ? null : durationMs;
			final var lastRunTs = rs.getTimestamp("last_run_at");
			return new YearCount(year, approxCount, lastRunTs == null ? null : lastRunTs.toInstant(), lastDurationMs);
		});
	}

	/**
	 * Empties the {@code transactions_<year>} partition. Refuses if a DVF import is
	 * currently running (would race the swap). The migration provisions years
	 * 2014..2030 — anything else would silently no-op and return success, so we
	 * reject it explicitly.
	 */
	public void truncateYear(int year) {
		if (year < 2014 || year > 2030) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Year " + year + " out of supported range [2014..2030]");
		}
		if (!jobExplorer.findRunningJobExecutions("dvfImportJob").isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"A DVF import is currently running — wait for it to finish before truncating");
		}
		final var table = "transactions_" + year;
		log.warn("Truncating partition {} via admin endpoint", table);
		jdbcTemplate.execute("TRUNCATE TABLE " + table);
	}

	public record YearCount(int year, long approxCount, Instant lastRunAt, Long lastDurationMs) {
	}
}
