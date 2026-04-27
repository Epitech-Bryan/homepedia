package com.homepedia.api.batch.dvf;

import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DvfBatchPersister {

	private final TransactionRepository transactionRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void clearAll() {
		final var existing = transactionRepository.count();
		if (existing > 0) {
			log.info("Clearing {} existing transactions before re-import...", existing);
			transactionRepository.deleteAllInBatch();
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveBatch(List<RealEstateTransaction> batch) {
		transactionRepository.saveAll(batch);
	}
}
