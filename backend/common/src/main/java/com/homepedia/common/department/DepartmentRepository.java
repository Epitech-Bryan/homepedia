package com.homepedia.common.department;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, String> {
	Optional<Department> findByCode(String code);

	List<Department> findByRegionCode(String regionCode);
}
