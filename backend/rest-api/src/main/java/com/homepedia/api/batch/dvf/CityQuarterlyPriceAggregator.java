package com.homepedia.api.batch.dvf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refreshes {@code city_price_quarterly_stats} for one year after its DVF
 * partition is swapped in. Same dedup / filter / surface-weighting logic as
 * {@link CityDvfStatsAggregator} but bucketed by (commune, year, quarter) so
 * the CityPage timeline can plot a 20-quarter trend without scanning the
 * partitioned transactions table at request time.
 *
 * <p>
 * Runs in its own transaction (REQUIRES_NEW) so a failure here can't roll back
 * the partition swap: the yearly aggregator already committed its refresh, and
 * the next DVF import will retry this quarterly one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CityQuarterlyPriceAggregator {

	private static final String DELETE_SQL = "DELETE FROM city_price_quarterly_stats WHERE year = ?";

	// Mirrors CityDvfStatsAggregator.INSERT_SQL but adds the quarter
	// derived from the mutation_date month, and groups by (insee, year, q).
	private static final String INSERT_SQL = """
			INSERT INTO city_price_quarterly_stats (insee_code, year, quarter, transaction_count, total_price, total_residential_surface, updated_at)
			SELECT
			  city_insee_code,
			  ?::int                                                                         AS year,
			  quarter,
			  COUNT(*)                                                                       AS transaction_count,
			  SUM(price)                                                                     AS total_price,
			  SUM(surface)                                                                   AS total_residential_surface,
			  NOW()                                                                          AS updated_at
			FROM (
			  SELECT
			    t.mutation_id,
			    ((EXTRACT(MONTH FROM MAX(t.mutation_date))::int - 1) / 3 + 1) AS quarter,
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
			GROUP BY city_insee_code, quarter
			""";

	private final JdbcTemplate jdbcTemplate;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void refreshYear(int year) {
		final var deleted = jdbcTemplate.update(DELETE_SQL, year);
		final var inserted = jdbcTemplate.update(INSERT_SQL, year, year, year);
		log.info("Refreshed city_price_quarterly_stats for year {}: -{} +{} rows", year, deleted, inserted);
	}
}
