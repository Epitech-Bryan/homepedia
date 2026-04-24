package com.homepedia.api.service;

import com.homepedia.api.mapper.ReviewMapper;
import com.homepedia.common.review.ReviewRepository;
import com.homepedia.common.review.ReviewSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

	private final ReviewRepository reviewRepository;

	public Page<ReviewSummary> findByCityInseeCode(final String cityInseeCode, final Pageable pageable) {
		return reviewRepository.findByCityInseeCode(cityInseeCode, pageable)
				.map(ReviewMapper.INSTANCE::convertToSummary);
	}
}
