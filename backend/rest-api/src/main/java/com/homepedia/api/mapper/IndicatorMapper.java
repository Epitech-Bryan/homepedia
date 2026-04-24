package com.homepedia.api.mapper;

import com.homepedia.common.indicator.Indicator;
import com.homepedia.common.indicator.IndicatorSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface IndicatorMapper {

	IndicatorMapper INSTANCE = Mappers.getMapper(IndicatorMapper.class);

	IndicatorSummary convertToSummary(Indicator indicator);

	List<IndicatorSummary> convertToSummaryList(List<Indicator> indicators);
}
