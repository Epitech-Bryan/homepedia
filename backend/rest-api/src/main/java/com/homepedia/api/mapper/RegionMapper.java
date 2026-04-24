package com.homepedia.api.mapper;

import com.homepedia.common.region.Region;
import com.homepedia.common.region.RegionSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface RegionMapper {

	RegionMapper INSTANCE = Mappers.getMapper(RegionMapper.class);

	RegionSummary convertToSummary(Region region);

	List<RegionSummary> convertToSummaryList(List<Region> regions);
}
