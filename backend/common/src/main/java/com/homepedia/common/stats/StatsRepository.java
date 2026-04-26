package com.homepedia.common.stats;

import com.homepedia.common.region.Region;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatsRepository extends JpaRepository<Region, String> {

	@Query(value = """
			SELECT
			  r.code AS code,
			  r.name AS name,
			  r.population AS population,
			  r.area AS area,
			  COUNT(t.id) AS transactionCount,
			  AVG(t.property_value) AS averagePrice,
			  AVG(CASE WHEN t.built_surface > 0 THEN t.property_value / t.built_surface END) AS averagePricePerSqm
			FROM regions r
			LEFT JOIN departments d ON d.region_code = r.code
			LEFT JOIN cities c ON c.department_code = d.code
			LEFT JOIN transactions t ON t.city_insee_code = c.insee_code
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
			  COUNT(t.id) AS transactionCount,
			  AVG(t.property_value) AS averagePrice,
			  AVG(CASE WHEN t.built_surface > 0 THEN t.property_value / t.built_surface END) AS averagePricePerSqm
			FROM departments d
			LEFT JOIN cities c ON c.department_code = d.code
			LEFT JOIN transactions t ON t.city_insee_code = c.insee_code
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
			  COUNT(t.id) AS transactionCount,
			  AVG(t.property_value) AS averagePrice,
			  AVG(CASE WHEN t.built_surface > 0 THEN t.property_value / t.built_surface END) AS averagePricePerSqm
			FROM cities c
			LEFT JOIN transactions t ON t.city_insee_code = c.insee_code
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
