package com.homepedia.common.review;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReviewRepository extends MongoRepository<CityReview, String> {
	Page<CityReview> findByCityInseeCode(String cityInseeCode, Pageable pageable);

	List<CityReview> findByCityInseeCode(String cityInseeCode);

	// Aggregated lookups for department / region scopes: the caller resolves the
	// set of INSEE codes under the area (via the relational cities table) and
	// pulls all matching reviews with a single {@code $in}. Country-level scopes
	// skip this entirely and use {@code findAll} — a 35 k-element {@code $in}
	// would be slower than a full scan.
	Page<CityReview> findByCityInseeCodeIn(Collection<String> cityInseeCodes, Pageable pageable);
}
