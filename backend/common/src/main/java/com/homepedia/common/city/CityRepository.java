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
}
