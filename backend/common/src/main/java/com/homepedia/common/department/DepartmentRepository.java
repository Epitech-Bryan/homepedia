package com.homepedia.common.department;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface DepartmentRepository extends JpaRepository<Department, String> {
	Optional<Department> findByCode(String code);

	List<Department> findByRegionCode(String regionCode);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Transactional
	@Query(value = """
			UPDATE departments SET
			  population = (SELECT SUM(c.population) FROM cities c
			               WHERE c.department_code = departments.code
			                 AND c.insee_code NOT BETWEEN '75101' AND '75120'
			                 AND c.insee_code NOT BETWEEN '13201' AND '13216'
			                 AND c.insee_code NOT BETWEEN '69381' AND '69389'),
			  area = (SELECT SUM(c.area) FROM cities c
			          WHERE c.department_code = departments.code
			            AND c.insee_code NOT BETWEEN '75101' AND '75120'
			            AND c.insee_code NOT BETWEEN '13201' AND '13216'
			            AND c.insee_code NOT BETWEEN '69381' AND '69389')
			""", nativeQuery = true)
	void recomputeAggregates();
}
