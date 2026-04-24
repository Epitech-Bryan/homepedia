package com.homepedia.common.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository
		extends
			JpaRepository<RealEstateTransaction, Long>,
			JpaSpecificationExecutor<RealEstateTransaction> {
	Page<RealEstateTransaction> findByCityInseeCode(String cityInseeCode, Pageable pageable);
}
