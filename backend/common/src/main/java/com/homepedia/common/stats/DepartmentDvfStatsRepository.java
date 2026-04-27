package com.homepedia.common.stats;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentDvfStatsRepository extends JpaRepository<DepartmentDvfStats, String> {

	List<DepartmentDvfStats> findAllByOrderByDepartmentCodeAsc();

	Optional<DepartmentDvfStats> findByDepartmentCode(String departmentCode);
}
