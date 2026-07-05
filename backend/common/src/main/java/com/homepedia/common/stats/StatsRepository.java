package com.homepedia.common.stats;

import com.homepedia.common.region.Region;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Stats aggregations all read from {@code city_dvf_yearly_stats}, a small
 * pre-aggregate populated by {@code CityDvfStatsAggregator} after every DVF
 * partition swap. The deduplication / filtering / surface-weighting that used
 * to scan the 20M-row {@code transactions} table on every API call now runs
 * once per import.
 *
 * <p>
 * Stored values are partial sums per (commune, year). Region/department/city
 * queries SUM them up, so the surface-weighted price/m² stays exact even when
 * only a subset of years has been imported. Communes without any DVF row for
 * the requested scope appear with COUNT 0 / NULL averages — same contract as
 * before for the API.
 *
 * <p>
 * Pre-migration data with {@code mutation_id IS NULL} is invisible here because
 * the aggregator's CTE filters it out — re-run the DVF imports per year to
 * repopulate the pre-agg.
 */
public interface StatsRepository extends JpaRepository<Region, String> {

	@Query(value = """
			SELECT
			  r.code AS code,
			  r.name AS name,
			  r.population AS population,
			  r.area AS area,
			  COALESCE(SUM(s.transaction_count), 0) AS transactionCount,
			  CASE WHEN COALESCE(SUM(s.transaction_count), 0) > 0
			       THEN (SUM(s.total_price) / SUM(s.transaction_count))::double precision
			       ELSE NULL END AS averagePrice,
			  CASE WHEN COALESCE(SUM(s.total_residential_surface), 0) > 0
			       THEN (SUM(s.total_price) / SUM(s.total_residential_surface))::double precision
			       ELSE NULL END AS averagePricePerSqm,
			  (SELECT SUM(i.indicator_value * (ASCII(SUBSTRING(i.label, 11, 1)) - 64))::double precision
			          / NULLIF(SUM(i.indicator_value), 0)
			   FROM indicators i
			   JOIN cities c2 ON c2.insee_code = i.geographic_code
			   JOIN departments d2 ON d2.code = c2.department_code
			   WHERE d2.region_code = r.code
			     AND i.geographic_level = 'CITY'
			     AND i.category = 'ENVIRONMENT'
			     AND i.label LIKE 'GES label _') AS pollutionScore
			FROM regions r
			LEFT JOIN departments d ON d.region_code = r.code
			LEFT JOIN cities c ON c.department_code = d.code
			LEFT JOIN city_dvf_yearly_stats s ON s.insee_code = c.insee_code
			GROUP BY r.code, r.name, r.population, r.area
			ORDER BY r.code
			""", nativeQuery = true)
	List<RegionStatsProjection> aggregateRegionStats();

	@Query(value = """
			SELECT
			  d.code AS code,
			  d.name AS name,
			  d.region_code AS regionCode,
			  d.population AS population,
			  d.area AS area,
			  COALESCE(SUM(s.transaction_count), 0) AS transactionCount,
			  CASE WHEN COALESCE(SUM(s.transaction_count), 0) > 0
			       THEN (SUM(s.total_price) / SUM(s.transaction_count))::double precision
			       ELSE NULL END AS averagePrice,
			  CASE WHEN COALESCE(SUM(s.total_residential_surface), 0) > 0
			       THEN (SUM(s.total_price) / SUM(s.total_residential_surface))::double precision
			       ELSE NULL END AS averagePricePerSqm,
			  (SELECT SUM(i.indicator_value * (ASCII(SUBSTRING(i.label, 11, 1)) - 64))::double precision
			          / NULLIF(SUM(i.indicator_value), 0)
			   FROM indicators i
			   JOIN cities c2 ON c2.insee_code = i.geographic_code
			   WHERE c2.department_code = d.code
			     AND i.geographic_level = 'CITY'
			     AND i.category = 'ENVIRONMENT'
			     AND i.label LIKE 'GES label _') AS pollutionScore
			FROM departments d
			LEFT JOIN cities c ON c.department_code = d.code
			LEFT JOIN city_dvf_yearly_stats s ON s.insee_code = c.insee_code
			WHERE :regionCode IS NULL OR d.region_code = :regionCode
			GROUP BY d.code, d.name, d.region_code, d.population, d.area
			ORDER BY d.code
			""", nativeQuery = true)
	List<DepartmentStatsProjection> aggregateDepartmentStats(@Param("regionCode") String regionCode);

	@Query(value = """
			SELECT
			  c.insee_code AS code,
			  c.name AS name,
			  c.department_code AS departmentCode,
			  c.population AS population,
			  c.area AS area,
			  COALESCE(SUM(s.transaction_count), 0) AS transactionCount,
			  CASE WHEN COALESCE(SUM(s.transaction_count), 0) > 0
			       THEN (SUM(s.total_price) / SUM(s.transaction_count))::double precision
			       ELSE NULL END AS averagePrice,
			  CASE WHEN COALESCE(SUM(s.total_residential_surface), 0) > 0
			       THEN (SUM(s.total_price) / SUM(s.total_residential_surface))::double precision
			       ELSE NULL END AS averagePricePerSqm,
			  ges.pollution AS pollutionScore
			FROM cities c
			LEFT JOIN city_dvf_yearly_stats s ON s.insee_code = c.insee_code
			LEFT JOIN LATERAL (
			  SELECT SUM(i.indicator_value * (ASCII(SUBSTRING(i.label, 11, 1)) - 64))::double precision
			         / NULLIF(SUM(i.indicator_value), 0) AS pollution
			  FROM indicators i
			  WHERE i.geographic_code = c.insee_code
			    AND i.geographic_level = 'CITY'
			    AND i.category = 'ENVIRONMENT'
			    AND i.label LIKE 'GES label _'
			) ges ON TRUE
			WHERE c.insee_code IN (:codes)
			GROUP BY c.insee_code, c.name, c.department_code, c.population, c.area, ges.pollution
			ORDER BY c.insee_code
			""", nativeQuery = true)
	List<CityStatsProjection> aggregateCityStats(@Param("codes") Collection<String> codes);

	/**
	 * Country-wide weighted GES score — the same A=1..G=7 aggregation the region /
	 * department queries run, but over every commune with DPE data. Returns
	 * {@code null} when no GES rows exist at all. Drives the national pollution
	 * figure on the country overview page.
	 */
	@Query(value = """
			SELECT SUM(i.indicator_value * (ASCII(SUBSTRING(i.label, 11, 1)) - 64))::double precision
			       / NULLIF(SUM(i.indicator_value), 0)
			FROM indicators i
			WHERE i.geographic_level = 'CITY'
			  AND i.category = 'ENVIRONMENT'
			  AND i.label LIKE 'GES label _'
			""", nativeQuery = true)
	Double aggregateCountryPollutionScore();

	interface RegionStatsProjection {
		String getCode();

		String getName();

		Long getPopulation();

		Double getArea();

		Long getTransactionCount();

		Double getAveragePrice();

		Double getAveragePricePerSqm();

		Double getPollutionScore();
	}

	interface DepartmentStatsProjection {
		String getCode();

		String getName();

		String getRegionCode();

		Long getPopulation();

		Double getArea();

		Long getTransactionCount();

		Double getAveragePrice();

		Double getAveragePricePerSqm();

		Double getPollutionScore();
	}

	interface CityStatsProjection {
		String getCode();

		String getName();

		String getDepartmentCode();

		Long getPopulation();

		Double getArea();

		Long getTransactionCount();

		Double getAveragePrice();

		Double getAveragePricePerSqm();

		Double getPollutionScore();
	}

	/**
	 * Pre-aggregated transaction stats for {@code TransactionService.computeStats}.
	 * Replaces the row-by-row stream-into-JVM aggregation that used to OOM the pod
	 * on busy departments — everything is computed DB-side in one query,
	 * partition-pruned by year and indexed on city/department.
	 *
	 * <p>
	 * Why not the {@code city_dvf_yearly_stats} pre-agg the region / dept / city
	 * queries above use ? Because that table applies the DVF-business filters
	 * (MAISON/APPARTEMENT only, surfaces 9-1000, prices 10k-5M, mutation_id dedup).
	 * The contract of {@code computeStats} is "raw row count + averages over valid
	 * prices" — using the pre-agg would silently drop rows the API still wants to
	 * count. A focused aggregate against the partitioned {@code transactions} table
	 * preserves the exact semantics the regression test locks down.
	 *
	 * <p>
	 * Median is computed via {@code OFFSET (validCount / 2) LIMIT 1} on the sorted
	 * valid-price rows, which reproduces the Java {@code prices.get(size / 2)}
	 * "upper-middle for even counts" behaviour — {@code percentile_disc(0.5)} would
	 * round differently for n=4.
	 */
	@Query(value = """
			WITH filtered AS (
			  SELECT t.property_value, t.built_surface
			  FROM transactions t
			  LEFT JOIN cities c ON c.insee_code = t.city_insee_code
			  WHERE (:cityCode IS NULL OR t.city_insee_code = :cityCode)
			    AND (:departmentCode IS NULL OR c.department_code = :departmentCode)
			    AND (:year IS NULL OR (t.mutation_date >= make_date(:year, 1, 1)
			                       AND t.mutation_date <  make_date(:year + 1, 1, 1)))
			),
			ordered_valid AS (
			  SELECT property_value,
			         ROW_NUMBER() OVER (ORDER BY property_value) AS rn,
			         COUNT(*)     OVER ()                        AS valid_count
			  FROM filtered
			  WHERE property_value > 0
			)
			SELECT
			  (SELECT COUNT(*) FROM filtered)                                       AS totalTransactions,
			  (SELECT AVG(property_value)::numeric(20,2)
			     FROM filtered WHERE property_value > 0)                            AS averagePrice,
			  (SELECT MIN(property_value)
			     FROM filtered WHERE property_value > 0)                            AS minPrice,
			  (SELECT MAX(property_value)
			     FROM filtered WHERE property_value > 0)                            AS maxPrice,
			  (SELECT property_value FROM ordered_valid
			     WHERE rn = (valid_count / 2) + 1 LIMIT 1)                          AS medianPrice,
			  (SELECT AVG(built_surface)::double precision
			     FROM filtered WHERE built_surface > 0)                             AS averageSurface,
			  (SELECT AVG(property_value::double precision / built_surface)
			     FROM filtered WHERE property_value > 0 AND built_surface > 0)      AS averagePricePerSqm
			""", nativeQuery = true)
	TransactionStatsProjection aggregateTransactionStats(@Param("cityCode") String cityCode,
			@Param("departmentCode") String departmentCode, @Param("year") Integer year);

	interface TransactionStatsProjection {
		Long getTotalTransactions();

		BigDecimal getAveragePrice();

		BigDecimal getMinPrice();

		BigDecimal getMaxPrice();

		BigDecimal getMedianPrice();

		Double getAverageSurface();

		Double getAveragePricePerSqm();
	}
}
