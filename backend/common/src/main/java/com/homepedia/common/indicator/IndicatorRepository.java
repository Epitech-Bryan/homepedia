package com.homepedia.common.indicator;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndicatorRepository extends JpaRepository<Indicator, Long> {
	List<Indicator> findByGeographicLevelAndGeographicCode(GeographicLevel level, String geographicCode);

	List<Indicator> findByGeographicLevelAndGeographicCodeAndCategory(GeographicLevel level, String geographicCode,
			IndicatorCategory category);

	/**
	 * Returns every {@code geographic_level = IRIS} indicator whose code lives
	 * under the given commune (IRIS codes start with the 5-char INSEE code, e.g.
	 * Paris 1er = 75101 + 4 digits per block). Empty until the Filosofi importer
	 * lands and seeds the table.
	 */
	@org.springframework.data.jpa.repository.Query("""
			SELECT i FROM Indicator i
			WHERE i.geographicLevel = com.homepedia.common.indicator.GeographicLevel.IRIS
			  AND i.geographicCode LIKE CONCAT(:communeInseeCode, '%')
			""")
	List<Indicator> findIrisIndicatorsByCommune(
			@org.springframework.data.repository.query.Param("communeInseeCode") String communeInseeCode);
}
