package com.homepedia.api.service;

import com.homepedia.api.mapper.TransactionMapper;
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
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

	private final TransactionRepository transactionRepository;

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
		// Refuse a fully-unscoped call: that streamed the entire 20 M-row
		// transactions table into the JVM heap and reliably OOM-killed the
		// pod. All real callers pass at least a commune, a department, or a
		// year — so this is safe.
		if (StringUtils.isBlank(cityInseeCode) && StringUtils.isBlank(departmentCode) && year == null) {
			return TransactionMapper.INSTANCE.emptyStats();
		}
		final var spec = buildSpecification(cityInseeCode, departmentCode, year, null, null, null);
		final var transactions = transactionRepository.findAll(spec);

		if (isEmpty(transactions)) {
			return TransactionMapper.INSTANCE.emptyStats();
		}

		final var prices = transactions.stream().map(RealEstateTransaction::getPropertyValue)
				.filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0).sorted().toList();

		if (isEmpty(prices)) {
			return TransactionMapper.INSTANCE.emptyStats();
		}

		final var sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		final var avg = sum.divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);
		final var median = prices.get(prices.size() / 2);
		final var min = prices.getFirst();
		final var max = prices.getLast();

		final double avgSurface = transactions.stream().map(RealEstateTransaction::getBuiltSurface)
				.filter(s -> s != null && s > 0).mapToDouble(Double::doubleValue).average().orElse(0.0);

		final double avgPricePerM2 = transactions.stream()
				.filter(t -> t.getPropertyValue() != null && t.getBuiltSurface() != null && t.getBuiltSurface() > 0
						&& t.getPropertyValue().compareTo(BigDecimal.ZERO) > 0)
				.mapToDouble(t -> t.getPropertyValue().doubleValue() / t.getBuiltSurface()).average().orElse(0.0);

		return new TransactionStats(transactions.size(), avg, median, min, max, avgSurface, avgPricePerM2);
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
