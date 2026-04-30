package com.homepedia.common.stats;

import com.homepedia.common.region.Region;
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
			       ELSE NULL END AS averagePricePerSqm
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
			       ELSE NULL END AS averagePricePerSqm
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
			       ELSE NULL END AS averagePricePerSqm
			FROM cities c
			LEFT JOIN city_dvf_yearly_stats s ON s.insee_code = c.insee_code
			WHERE c.insee_code IN (:codes)
			GROUP BY c.insee_code, c.name, c.department_code, c.population, c.area
			ORDER BY c.insee_code
			""", nativeQuery = true)
	List<CityStatsProjection> aggregateCityStats(@Param("codes") Collection<String> codes);

	interface RegionStatsProjection {
		String getCode();

		String getName();

		Long getPopulation();

		Double getArea();

		Long getTransactionCount();

		Double getAveragePrice();

		Double getAveragePricePerSqm();
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
	}
}
