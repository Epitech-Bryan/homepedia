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
 */
public interface CityTileStatsRepository extends JpaRepository<City, String> {

	@Query(value = """
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
			       ELSE NULL END                                                          AS averagePricePerSqm
			FROM cities c
			LEFT JOIN city_dvf_yearly_stats s ON s.insee_code = c.insee_code
			GROUP BY c.insee_code, c.population, c.area
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
	}
}
