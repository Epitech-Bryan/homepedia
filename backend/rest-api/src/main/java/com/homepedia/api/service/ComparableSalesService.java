package com.homepedia.api.service;

import com.homepedia.common.transaction.ComparableSale;
import com.homepedia.common.transaction.PropertyType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import static com.homepedia.api.config.CacheConfig.CACHE_STATS;

/**
 * Reads the {@code comparable_transactions} pre-aggregate (changeset 015)
 * joined back to the geocoded source row so the popup gets everything it needs
 * in one round-trip — no second fetch per comparable.
 *
 * <p>
 * Returns an empty list until the ComparableSalesAggregator Spark job populates
 * the table. The endpoint is live now so the frontend can be developed against
 * the final contract.
 */
@Service
@RequiredArgsConstructor
public class ComparableSalesService {

	private static final String SQL = """
			SELECT
			  c.similarity_rank,
			  c.comparable_id,
			  t.mutation_date,
			  t.property_value,
			  t.property_type,
			  t.built_surface,
			  t.room_count,
			  t.latitude,
			  t.longitude,
			  c.distance_m,
			  c.price_delta_pct
			FROM comparable_transactions c
			JOIN transactions t ON t.id = c.comparable_id
			WHERE c.transaction_id = ?
			ORDER BY c.similarity_rank
			""";

	// Hoisted to static so we don't allocate a fresh lambda + closure on every
	// popup hit. The mapper is stateless and the result of an N=10 read is
	// dominated by the JOIN anyway, but the lambda capture is pure waste.
	private static final RowMapper<ComparableSale> ROW_MAPPER = (rs, i) -> new ComparableSale(
			rs.getInt("similarity_rank"), rs.getLong("comparable_id"), rs.getDate("mutation_date").toLocalDate(),
			rs.getBigDecimal("property_value"), PropertyType.valueOf(rs.getString("property_type")),
			(Double) rs.getObject("built_surface"), (Integer) rs.getObject("room_count"),
			(Double) rs.getObject("latitude"), (Double) rs.getObject("longitude"), (Integer) rs.getObject("distance_m"),
			rs.getBigDecimal("price_delta_pct"));

	private final JdbcTemplate jdbcTemplate;

	@Cacheable(value = CACHE_STATS, key = "'comparables:' + #transactionId")
	public List<ComparableSale> findByTransactionId(final long transactionId) {
		return jdbcTemplate.query(SQL, ROW_MAPPER, transactionId);
	}
}
