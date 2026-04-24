package com.homepedia.api.mapper;

import com.homepedia.common.city.City;
import com.homepedia.common.city.CitySummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CityMapper {

	CityMapper INSTANCE = Mappers.getMapper(CityMapper.class);

	@Mapping(source = "department.code", target = "departmentCode")
	@Mapping(source = "department.name", target = "departmentName")
	CitySummary convertToSummary(City city);

	List<CitySummary> convertToSummaryList(List<City> cities);
}
