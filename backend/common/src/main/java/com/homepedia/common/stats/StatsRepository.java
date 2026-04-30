package com.homepedia.common.stats;

import com.homepedia.common.region.Region;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatsRepository extends JpaRepository<Region, String> {

	/*
	 * Stats aggregations all share the same shape:
	 *
	 * 1. Deduplicate first via mutation_dedup CTE — DVF publishes one row per "lot"
	 * of a sale (parking + flat + cellar = 3 rows, same mutation_id, same total
	 * valeur_fonciere). Without dedup we'd multiply the count and over-weight
	 * multi-lot mutations in AVG. Per mutation we keep MAX(property_value) (any row
	 * has the total price), SUM(built_surface) restricted to residential lots
	 * (MAISON/APPARTEMENT) so the price/m² uses only the living surface, and any
	 * one matching insee_code (rows of one mutation are in the same commune). 2.
	 * Filter out non-market mutations (Échange, Expropriation, Adjudication,
	 * terrain à bâtir) and outliers (price < 10 k€ or > 5 M€ → almost always
	 * intra-family transfers / commercial portfolios; total residential surface < 9
	 * m² or > 1000 m² → cellar-only sales / commercial buildings). 3. Compute
	 * price/m² as SUM(price) / SUM(surface) — i.e. a surface-weighted mean rather
	 * than the arithmetic mean of the per-mutation ratios. The unweighted AVG
	 * amplifies tiny-surface outliers (a 1 m² row at 50 k€ would contribute 50 000
	 * €/m² to the average, dwarfing a 100 m² flat at 3 000 €/m²).
	 *
	 * Existing rows imported before migration 007 have mutation_id NULL and are
	 * excluded from all aggregations. Re-run the DVF imports per year to repopulate
	 * them.
	 */
	String MUTATION_DEDUP_CTE = """
			WITH mutation_dedup AS (
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
			  WHERE t.mutation_id IS NOT NULL
			    AND t.mutation_nature IN ('Vente', 'Vente en l''état futur d''achèvement')
			    AND t.property_value BETWEEN 10000 AND 5000000
			  GROUP BY t.mutation_id
			  HAVING SUM(CASE WHEN t.property_type IN ('MAISON','APPARTEMENT')
			                   AND t.built_surface BETWEEN 9 AND 1000
			                  THEN t.built_surface END) IS NOT NULL
			)
			""";

	@Query(value = MUTATION_DEDUP_CTE + """
			SELECT
			  r.code AS code,
			  r.name AS name,
			  r.population AS population,
			  r.area AS area,
			  COUNT(t.mutation_id) AS transactionCount,
			  AVG(t.price) AS averagePrice,
			  (SUM(t.price) / NULLIF(SUM(t.surface), 0))::double precision AS averagePricePerSqm
			FROM regions r
			LEFT JOIN departments d ON d.region_code = r.code
			LEFT JOIN cities c ON c.department_code = d.code
			LEFT JOIN mutation_dedup t ON t.city_insee_code = c.insee_code
			GROUP BY r.code, r.name, r.population, r.area
			ORDER BY r.code
			""", nativeQuery = true)
	List<RegionStatsProjection> aggregateRegionStats();

	@Query(value = MUTATION_DEDUP_CTE + """
			SELECT
			  d.code AS code,
			  d.name AS name,
			  d.region_code AS regionCode,
			  d.population AS population,
			  d.area AS area,
			  COUNT(t.mutation_id) AS transactionCount,
			  AVG(t.price) AS averagePrice,
			  (SUM(t.price) / NULLIF(SUM(t.surface), 0))::double precision AS averagePricePerSqm
			FROM departments d
			LEFT JOIN cities c ON c.department_code = d.code
			LEFT JOIN mutation_dedup t ON t.city_insee_code = c.insee_code
			WHERE :regionCode IS NULL OR d.region_code = :regionCode
			GROUP BY d.code, d.name, d.region_code, d.population, d.area
			ORDER BY d.code
			""", nativeQuery = true)
	List<DepartmentStatsProjection> aggregateDepartmentStats(@Param("regionCode") String regionCode);

	@Query(value = MUTATION_DEDUP_CTE + """
			SELECT
			  c.insee_code AS code,
			  c.name AS name,
			  c.department_code AS departmentCode,
			  c.population AS population,
			  c.area AS area,
			  COUNT(t.mutation_id) AS transactionCount,
			  AVG(t.price) AS averagePrice,
			  (SUM(t.price) / NULLIF(SUM(t.surface), 0))::double precision AS averagePricePerSqm
			FROM cities c
			LEFT JOIN mutation_dedup t ON t.city_insee_code = c.insee_code
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
