package com.homepedia.api.batch.dvf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refreshes {@code city_dvf_yearly_stats} for one year after its DVF partition
 * is swapped in. The expensive mutation_dedup / filter / surface-weighting
 * happens here exactly once per import; the API stats queries then become
 * trivial sums on a 36 k-row table instead of CTE scans of 20 M+ rows.
 *
 * <p>
 * Runs in its own transaction (REQUIRES_NEW) so it can't roll back the import:
 * if the aggregation fails for any reason the partition swap stays committed
 * and the next import will refresh.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CityDvfStatsAggregator {

	private static final String DELETE_SQL = "DELETE FROM city_dvf_yearly_stats WHERE year = ?";

	// Same dedup / filter logic as StatsRepository.MUTATION_DEDUP_CTE but
	// scoped to one year (planner can pick the partition directly via the
	// transactions_<year> partition pruning) and aggregated to commune level
	// for storage. Run once per import — the API never sees this query.
	private static final String INSERT_SQL = """
			INSERT INTO city_dvf_yearly_stats (insee_code, year, transaction_count, total_price, total_residential_surface, updated_at)
			SELECT
			  city_insee_code,
			  ?::int AS year,
			  COUNT(*)                                                                       AS transaction_count,
			  SUM(price)                                                                     AS total_price,
			  SUM(surface)                                                                   AS total_residential_surface,
			  NOW()                                                                          AS updated_at
			FROM (
			  SELECT
			    t.mutation_id,
			    MAX(t.property_value) AS price,
			    SUM(CASE WHEN t.property_type IN ('MAISON','APPARTEMENT')
			              AND t.built_surface BETWEEN 9 AND 1000
			             THEN t.built_surface END) AS surface,
			    MAX(t.city_insee_code) FILTER (
			      WHERE t.property_type IN ('MAISON','APPARTEMENT')
			    ) AS city_insee_code
			  FROM transactions t
			  WHERE t.mutation_date >= make_date(?::int, 1, 1)
			    AND t.mutation_date <  make_date((?::int + 1), 1, 1)
			    AND t.mutation_id IS NOT NULL
			    AND t.mutation_nature IN ('Vente', 'Vente en l''état futur d''achèvement')
			    AND t.property_value BETWEEN 10000 AND 5000000
			  GROUP BY t.mutation_id
			  HAVING SUM(CASE WHEN t.property_type IN ('MAISON','APPARTEMENT')
			                   AND t.built_surface BETWEEN 9 AND 1000
			                  THEN t.built_surface END) IS NOT NULL
			) m
			WHERE city_insee_code IS NOT NULL
			GROUP BY city_insee_code
			""";

	private final JdbcTemplate jdbcTemplate;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void refreshYear(int year) {
		final var deleted = jdbcTemplate.update(DELETE_SQL, year);
		// year passed 3 times: ?::int as year value, lower bound, upper bound.
		final var inserted = jdbcTemplate.update(INSERT_SQL, year, year, year);
		log.info("Refreshed city_dvf_yearly_stats for year {}: -{} +{} rows", year, deleted, inserted);
	}
}
