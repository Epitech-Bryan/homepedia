package com.homepedia.common.geo;

import com.homepedia.common.indicator.GeographicLevel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeoJsonBoundaryRepository extends JpaRepository<GeoJsonBoundary, Long> {
	List<GeoJsonBoundary> findByGeographicLevel(GeographicLevel geographicLevel);

	Optional<GeoJsonBoundary> findByGeographicLevelAndGeographicCode(GeographicLevel level, String code);
}
