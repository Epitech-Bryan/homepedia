package com.homepedia.common.stats;

import com.homepedia.common.region.Region;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads the {@code city_price_quarterly_stats} pre-aggregate populated by
 * {@code CityQuarterlyPriceAggregator} after every DVF partition swap. The
 * commune timeline endpoint pulls one row per quarter from this table — 20 rows
 * max per request (5 years × 4 quarters), no scan of the partitioned
 * transactions table on the hot path.
 *
 * <p>
 * Extends a placeholder JPA repository over Region just to give Spring Data a
 * managed entity to anchor on; the actual query is a native projection and
 * doesn't touch the Region table.
 */
public interface QuarterlyPriceRepository extends JpaRepository<Region, String> {

	@Query(value = """
			SELECT
			  s.year AS year,
			  s.quarter AS quarter,
			  COALESCE(s.transaction_count, 0) AS transactionCount,
			  CASE WHEN COALESCE(s.total_residential_surface, 0) > 0
			       THEN (s.total_price / s.total_residential_surface)::double precision
			       ELSE NULL END AS averagePricePerSqm
			FROM city_price_quarterly_stats s
			WHERE s.insee_code = :inseeCode
			ORDER BY s.year, s.quarter
			""", nativeQuery = true)
	List<QuarterlyPriceProjection> findTimelineByInsee(@Param("inseeCode") String inseeCode);

	interface QuarterlyPriceProjection {
		Integer getYear();

		Integer getQuarter();

		Long getTransactionCount();

		Double getAveragePricePerSqm();
	}
}
