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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.homepedia.api.config.CacheConfig.CACHE_STATS;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

	private static final String STATS_SQL = """
			SELECT
			  COUNT(*)                                                  AS cnt,
			  AVG(price)                                                AS avg_price,
			  percentile_cont(0.5) WITHIN GROUP (ORDER BY price)        AS median_price,
			  MIN(price)                                                AS min_price,
			  MAX(price)                                                AS max_price,
			  AVG(surface)                                              AS avg_surface,
			  CASE WHEN SUM(surface) > 0
			       THEN SUM(price)/SUM(surface) END                     AS avg_pricesqm
			FROM (
			  SELECT
			    t.mutation_id,
			    MAX(t.property_value) AS price,
			    SUM(CASE WHEN t.property_type IN ('MAISON','APPARTEMENT')
			              AND t.built_surface BETWEEN 9 AND 1000
			             THEN t.built_surface END) AS surface
			  FROM transactions t
			  JOIN cities c ON c.insee_code = t.city_insee_code
			  WHERE t.mutation_id IS NOT NULL
			    AND t.mutation_nature IN ('Vente', 'Vente en l''état futur d''achèvement')
			    AND t.property_value BETWEEN 10000 AND 5000000
			    AND (CAST(? AS varchar) IS NULL OR c.insee_code      = CAST(? AS varchar))
			    AND (CAST(? AS varchar) IS NULL OR c.department_code = CAST(? AS varchar))
			    AND (CAST(? AS int)     IS NULL OR (t.mutation_date >= make_date(CAST(? AS int), 1, 1)
			                                    AND t.mutation_date <  make_date(CAST(? AS int) + 1, 1, 1)))
			  GROUP BY t.mutation_id
			  HAVING SUM(CASE WHEN t.property_type IN ('MAISON','APPARTEMENT')
			                   AND t.built_surface BETWEEN 9 AND 1000
			                  THEN t.built_surface END) IS NOT NULL
			) m
			""";

	private final TransactionRepository transactionRepository;
	private final JdbcTemplate jdbcTemplate;

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

	/**
	 * Aggregate stats in SQL — never load rows. The previous version did
	 * {@code findAll(spec)} which streamed entire department-sized result sets into
	 * the JVM heap (observed: 4 GB of heap blown out by a single
	 * /transactions/stats?departmentCode=75 hit). The CTE deduplicates by
	 * {@code mutation_id} (DVF publishes one row per lot) and applies the same
	 * filters as the pre-aggregated stats: only Vente/VEFA, residential lots
	 * (MAISON/APPARTEMENT, 9..1000 m²), price 10 k€..5 M€.
	 *
	 * <p>
	 * Refuses an unscoped call (all three params null) — that's a 20M-row scan with
	 * no business value; clients should always pass at least a commune, a
	 * department, or a year.
	 */
	public TransactionStats computeStats(final String cityInseeCode, final String departmentCode, final Integer year) {
		if (StringUtils.isBlank(cityInseeCode) && StringUtils.isBlank(departmentCode) && year == null) {
			return TransactionMapper.INSTANCE.emptyStats();
		}
		final var stats = jdbcTemplate.queryForObject(STATS_SQL, (rs, i) -> {
			final long count = rs.getLong("cnt");
			if (count == 0) {
				return TransactionMapper.INSTANCE.emptyStats();
			}
			final var avg = rs.getBigDecimal("avg_price");
			final var median = rs.getBigDecimal("median_price");
			final var min = rs.getBigDecimal("min_price");
			final var max = rs.getBigDecimal("max_price");
			final double avgSurface = rs.getDouble("avg_surface");
			final boolean avgSurfaceWasNull = rs.wasNull();
			final double avgPricePerSqm = rs.getDouble("avg_pricesqm");
			final boolean avgPricePerSqmWasNull = rs.wasNull();
			return new TransactionStats(count, nullSafe(avg), nullSafe(median), nullSafe(min), nullSafe(max),
					avgSurfaceWasNull ? 0.0 : avgSurface, avgPricePerSqmWasNull ? 0.0 : avgPricePerSqm);
		}, cityInseeCode, cityInseeCode, departmentCode, departmentCode, year, year, year);
		return stats != null ? stats : TransactionMapper.INSTANCE.emptyStats();
	}

	private static BigDecimal nullSafe(BigDecimal value) {
		return value != null ? value : BigDecimal.ZERO;
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
