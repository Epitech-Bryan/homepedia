package com.homepedia.common.indicator;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndicatorRepository extends JpaRepository<Indicator, Long> {
	List<Indicator> findByGeographicLevelAndGeographicCode(GeographicLevel level, String geographicCode);

	List<Indicator> findByGeographicLevelAndGeographicCodeAndCategory(GeographicLevel level, String geographicCode,
			IndicatorCategory category);
}
