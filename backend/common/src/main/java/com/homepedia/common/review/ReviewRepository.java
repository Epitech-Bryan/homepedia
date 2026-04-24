package com.homepedia.common.review;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<CityReview, Long> {
	Page<CityReview> findByCityInseeCode(String cityInseeCode, Pageable pageable);

	List<CityReview> findByCityInseeCode(String cityInseeCode);
}
