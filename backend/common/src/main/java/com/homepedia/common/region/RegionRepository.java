package com.homepedia.common.region;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, String> {
	Optional<Region> findByCode(String code);
}
