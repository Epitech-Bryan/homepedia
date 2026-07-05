package com.homepedia.common.city;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CityRepository extends JpaRepository<City, String> {
	Optional<City> findByInseeCode(String inseeCode);

	List<City> findByDepartmentCode(String departmentCode);

	Page<City> findByDepartmentCode(String departmentCode, Pageable pageable);

	@Query("SELECT c FROM City c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))")
	Page<City> searchByName(String query, Pageable pageable);

	// Projection used by the review generator — it only needs the INSEE
	// code to seed deterministic per-city review templates. Pulling the
	// full City entity for all ~35 k rows added ~250 MB of heap churn for
	// columns the consumer never touches.
	@Query("SELECT c.inseeCode FROM City c")
	List<String> findAllInseeCodes();

	// INSEE-code projections used to resolve a department / region scope into
	// the set of communes whose Mongo reviews should be aggregated. Lightweight
	// on purpose — the review aggregation only ever needs the keys.
	@Query("SELECT c.inseeCode FROM City c WHERE c.department.code = :departmentCode")
	List<String> findInseeCodesByDepartmentCode(String departmentCode);

	@Query("SELECT c.inseeCode FROM City c WHERE c.department.region.code = :regionCode")
	List<String> findInseeCodesByRegionCode(String regionCode);
}
