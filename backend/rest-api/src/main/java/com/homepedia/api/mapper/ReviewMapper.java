package com.homepedia.api.mapper;

import com.homepedia.common.review.CityReview;
import com.homepedia.common.review.ReviewSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ReviewMapper {

	ReviewMapper INSTANCE = Mappers.getMapper(ReviewMapper.class);

	ReviewSummary convertToSummary(CityReview review);

	List<ReviewSummary> convertToSummaryList(List<CityReview> reviews);
}
