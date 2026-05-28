package com.homepedia.api.service;

import com.homepedia.common.transaction.PropertyType;
import com.homepedia.common.transaction.TransactionMarker;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import static com.homepedia.api.config.CacheConfig.CACHE_STATS;

/**
 * Per-transaction marker layer for the map, complementary to
 * {@link TransactionHeatPointService}. Where the heatmap aggregates into a
 * grid, this returns individual geocoded mutations so users can click a pin and
 * see "this T3 of 65 m² sold for 380 k€ in June 2024". Only meant for close-in
 * zooms — a wide viewport would spam the map and the wire, so the service
 * rejects bboxes wider than {@link #maxBboxDegrees} (default 0.2°, ≈ 22 km) and
 * hard-caps the result list.
 *
 * <p>
 * Hits the partial {@code idx_transaction_geocoded_bbox} index from changeset
 * 010, so the lookup stays cheap regardless of how many partitions are
 * attached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionMarkerService {

	/**
	 * Default ceiling on returned markers — enough for a dense city block, capped
	 * before the JSON gets unwieldy.
	 */
	private static final int DEFAULT_LIMIT = 300;

	/**
	 * Hard ceiling regardless of client {@code limit}; mirrors the heatmap's bias
	 * against megabyte responses.
	 */
	private static final int MAX_LIMIT = 1000;

	/**
	 * Reject viewports wider than this many degrees. At ~0.2° (city scale) we'll
	 * typically return a few hundred markers per dense commune; beyond that the
	 * heatmap layer takes over.
	 */
	@Value("${homepedia.markers.max-bbox-degrees:0.2}")
	private double maxBboxDegrees;

	private final JdbcTemplate jdbcTemplate;

	@Cacheable(value = CACHE_STATS, key = "'markers:' + #propertyType + ':' + #minPrice + ':' + #maxPrice + ':' + #limit + ':'"
			+ " + T(java.lang.Math).round(#south * 1000) + ':' + T(java.lang.Math).round(#west * 1000) + ':'"
			+ " + T(java.lang.Math).round(#north * 1000) + ':' + T(java.lang.Math).round(#east * 1000)")
	public List<TransactionMarker> markers(final double south, final double west, final double north, final double east,
			final PropertyType propertyType, final BigDecimal minPrice, final BigDecimal maxPrice,
			final Integer limit) {
		if (north <= south || east <= west) {
			return List.of();
		}
		if ((north - south) > maxBboxDegrees || (east - west) > maxBboxDegrees) {
			return List.of();
		}

		final var effectiveLimit = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

		final var sql = new StringBuilder("""
				SELECT id, latitude, longitude, mutation_date, property_value, property_type, built_surface, room_count
				FROM transactions
				WHERE latitude IS NOT NULL
				  AND latitude BETWEEN ? AND ?
				  AND longitude BETWEEN ? AND ?
				  AND property_value > 0
				""");
		final var params = new ArrayList<Object>(List.of(south, north, west, east));
		if (propertyType != null) {
			sql.append(" AND property_type = ?");
			params.add(propertyType.name());
		}
		if (minPrice != null) {
			sql.append(" AND property_value >= ?");
			params.add(minPrice);
		}
		if (maxPrice != null) {
			sql.append(" AND property_value <= ?");
			params.add(maxPrice);
		}
		sql.append(" ORDER BY mutation_date DESC LIMIT ").append(effectiveLimit);

		return jdbcTemplate.query(sql.toString(), (rs, i) -> {
			// property_type / mutation_date can be NULL for DVF rows whose
			// local type didn't map to a known PropertyType. Now that
			// transactions carry geo-dvf coordinates this mapper actually runs
			// (previously the un-geocoded table returned zero rows), so guard
			// the nullable columns instead of NPE-ing on valueOf(null).
			final var typeStr = rs.getString("property_type");
			final var date = rs.getDate("mutation_date");
			return new TransactionMarker(rs.getLong("id"), rs.getDouble("latitude"), rs.getDouble("longitude"),
					date != null ? date.toLocalDate() : null, rs.getBigDecimal("property_value"),
					typeStr != null ? PropertyType.valueOf(typeStr) : null, (Double) rs.getObject("built_surface"),
					(Integer) rs.getObject("room_count"));
		}, params.toArray());
	}
}
