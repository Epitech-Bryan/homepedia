package com.homepedia.api.service;

import com.homepedia.api.mapper.TransactionMapper;
import com.homepedia.common.stats.StatsRepository;
import com.homepedia.common.transaction.PropertyType;
import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionDetail;
import com.homepedia.common.transaction.TransactionRepository;
import com.homepedia.common.transaction.TransactionStats;
import com.homepedia.common.transaction.TransactionSummary;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.homepedia.api.config.CacheConfig.CACHE_STATS;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

	private final TransactionRepository transactionRepository;

	private final StatsRepository statsRepository;

	public Page<TransactionSummary> search(final String cityInseeCode, final String departmentCode, final Integer year,
			final BigDecimal minPrice, final BigDecimal maxPrice, final PropertyType propertyType,
			final Pageable pageable) {
		final var spec = buildSpecification(cityInseeCode, departmentCode, year, minPrice, maxPrice, propertyType);
		return transactionRepository.findAll(spec, pageable).map(TransactionMapper.INSTANCE::convertToSummary);
	}

	@Cacheable(value = CACHE_STATS, key = "'transaction:' + #id")
	public Optional<TransactionDetail> findById(final Long id) {
		return transactionRepository.findById(id).map(TransactionMapper.INSTANCE::convertToDetail);
	}

	public TransactionStats computeStats(final String cityInseeCode, final String departmentCode, final Integer year) {
		// Refuse a fully-unscoped call: the old impl streamed the entire 20 M-row
		// transactions table into the JVM heap and reliably OOM-killed the
		// pod. The new pre-agg path wouldn't OOM but a full-France scan is
		// still meaningless — the guard stays as belt-and-braces.
		if (StringUtils.isBlank(cityInseeCode) && StringUtils.isBlank(departmentCode) && year == null) {
			return TransactionMapper.INSTANCE.emptyStats();
		}
		final var p = statsRepository.aggregateTransactionStats(StringUtils.trimToNull(cityInseeCode),
				StringUtils.trimToNull(departmentCode), year);
		if (p == null || p.getTotalTransactions() == null || p.getTotalTransactions() == 0L
				|| p.getAveragePrice() == null) {
			// Either no rows matched, or every match had a null/zero price —
			// the old in-JVM path collapsed both into emptyStats(), keep it.
			return TransactionMapper.INSTANCE.emptyStats();
		}
		final var avg = scaleOrZero(p.getAveragePrice());
		return new TransactionStats(p.getTotalTransactions(), avg, nonNull(p.getMedianPrice()),
				nonNull(p.getMinPrice()), nonNull(p.getMaxPrice()),
				Optional.ofNullable(p.getAverageSurface()).orElse(0.0),
				Optional.ofNullable(p.getAveragePricePerSqm()).orElse(0.0));
	}

	private static BigDecimal scaleOrZero(final BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal nonNull(final BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private Specification<RealEstateTransaction> buildSpecification(final String cityInseeCode,
			final String departmentCode, final Integer year, final BigDecimal minPrice, final BigDecimal maxPrice,
			final PropertyType propertyType) {
		return (root, query, cb) -> {
			final var predicates = new ArrayList<Predicate>();
			if (StringUtils.isNotBlank(cityInseeCode)) {
				predicates.add(cb.equal(root.get("city").get("inseeCode"), cityInseeCode));
			}
			if (StringUtils.isNotBlank(departmentCode)) {
				predicates.add(cb.equal(root.get("city").get("department").get("code"), departmentCode));
			}
			if (year != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("mutationDate"), LocalDate.of(year, 1, 1)));
				predicates.add(cb.lessThan(root.get("mutationDate"), LocalDate.of(year + 1, 1, 1)));
			}
			if (minPrice != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("propertyValue"), minPrice));
			}
			if (maxPrice != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("propertyValue"), maxPrice));
			}
			if (propertyType != null) {
				predicates.add(cb.equal(root.get("propertyType"), propertyType));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}
}
