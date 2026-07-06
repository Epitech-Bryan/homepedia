package com.homepedia.api.batch.tiles;

import com.homepedia.common.city.City;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * One-row-per-commune projection of the metrics the choropleth needs to colour
 * each polygon — population, area, transaction count, average price and average
 * €/m². Returned in INSEE code order so the streaming GeoJSON enricher can sort
 * the polygon collection once and zip the two streams without holding the whole
 * stats map in memory.
 *
 * <p>
 * Reads from the {@code city_dvf_yearly_stats} pre-agg (same source as
 * {@code StatsRepository.aggregateCityStats}); communes with no DVF rows
 * surface as COUNT 0 / NULL averages so the tile feature still ships their
 * population and area.
 *
 * <p>
 * {@code pollutionScore} is the GES (greenhouse-gas) class averaged per
 * commune: the ADEME DPE feed ships a GES letter A..G per dwelling, stored as
 * {@code "GES label X"} percentages in {@code indicators}. The lateral maps
 * each letter to a weight via the ASCII offset (A=65 maps to 1, G=71 to 7) so a
 * commune of GES-A buildings scores about 1 (clean) and one of GES-G about 7
 * (very polluting). NULL when no GES rows exist.
 *
 * <p>
 * Comments are kept out of the {@code @Query} string itself: Spring Data scans
 * the native query for quotes / SpEL and an apostrophe inside a SQL comment
 * opens a quoted range it never closes, which blows up bean creation.
 */
public interface CityTileStatsRepository extends JpaRepository<City, String> {

	@Query(value = """
			WITH dept_ges AS (
			  SELECT c2.department_code AS dept,
			         SUM(i.indicator_value * (ASCII(SUBSTRING(i.label, 11, 1)) - 64))::double precision
			         / NULLIF(SUM(i.indicator_value), 0) AS pollution
			  FROM indicators i
			  JOIN cities c2 ON c2.insee_code = i.geographic_code
			  WHERE i.geographic_level = 'CITY'
			    AND i.category = 'ENVIRONMENT'
			    AND i.label LIKE 'GES label _'
			  GROUP BY c2.department_code
			)
			SELECT
			  c.insee_code                                                                AS code,
			  c.population                                                                AS population,
			  c.area                                                                      AS area,
			  COALESCE(SUM(s.transaction_count), 0)                                       AS transactionCount,
			  CASE WHEN COALESCE(SUM(s.transaction_count), 0) > 0
			       THEN (SUM(s.total_price) / SUM(s.transaction_count))::double precision
			       ELSE NULL END                                                          AS averagePrice,
			  CASE WHEN COALESCE(SUM(s.total_residential_surface), 0) > 0
			       THEN (SUM(s.total_price) / SUM(s.total_residential_surface))::double precision
			       ELSE NULL END                                                          AS averagePricePerSqm,
			  COALESCE(ges.pollution, dg.pollution)                                       AS pollutionScore
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
			LEFT JOIN dept_ges dg ON dg.dept = c.department_code
			GROUP BY c.insee_code, c.population, c.area, ges.pollution, dg.pollution
			ORDER BY c.insee_code
			""", nativeQuery = true)
	List<CityTileStatsProjection> findAllForTiles();

	interface CityTileStatsProjection {
		String getCode();

		Long getPopulation();

		Double getArea();

		Long getTransactionCount();

		Double getAveragePrice();

		Double getAveragePricePerSqm();

		Double getPollutionScore();
	}
}
