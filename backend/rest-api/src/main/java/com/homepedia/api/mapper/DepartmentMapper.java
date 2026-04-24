package com.homepedia.api.mapper;

import com.homepedia.common.department.Department;
import com.homepedia.common.department.DepartmentSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface DepartmentMapper {

	DepartmentMapper INSTANCE = Mappers.getMapper(DepartmentMapper.class);

	@Mapping(source = "region.code", target = "regionCode")
	@Mapping(source = "region.name", target = "regionName")
	DepartmentSummary convertToSummary(Department department);

	List<DepartmentSummary> convertToSummaryList(List<Department> departments);
}
