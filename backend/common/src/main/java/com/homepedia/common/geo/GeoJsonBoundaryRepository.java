package com.homepedia.common.geo;

import com.homepedia.common.indicator.GeographicLevel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GeoJsonBoundaryRepository extends JpaRepository<GeoJsonBoundary, Long> {
	List<GeoJsonBoundary> findByGeographicLevel(GeographicLevel geographicLevel);

	Optional<GeoJsonBoundary> findByGeographicLevelAndGeographicCode(GeographicLevel level, String code);

	/**
	 * Every IRIS polygon whose 9-char code starts with the 5-char commune INSEE.
	 * The {@code varchar_pattern_ops} partial index on
	 * {@code (geographic_code) WHERE level='IRIS'} (changeset 016) makes this a
	 * range scan instead of a sequential one — required for the
	 * {@code /geo/iris/{insee}} endpoint to stay sub-100 ms once Filosofi imports
	 * the ~50k IRIS rows.
	 */
	@Query(value = """
			SELECT b.* FROM geo_boundaries b
			WHERE b.geographic_level = 'IRIS'
			  AND b.geographic_code LIKE :inseePrefix
			""", nativeQuery = true)
	List<GeoJsonBoundary> findIrisBoundariesByCommune(@Param("inseePrefix") String inseePrefix);
}
