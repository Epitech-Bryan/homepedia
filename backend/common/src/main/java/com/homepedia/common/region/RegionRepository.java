package com.homepedia.common.region;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RegionRepository extends JpaRepository<Region, String> {
	Optional<Region> findByCode(String code);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Transactional
	@Query(value = """
			UPDATE regions SET
			  population = (SELECT SUM(d.population) FROM departments d WHERE d.region_code = regions.code),
			  area = (SELECT SUM(d.area) FROM departments d WHERE d.region_code = regions.code)
			""", nativeQuery = true)
	void recomputeAggregates();
}
