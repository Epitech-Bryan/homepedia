package com.homepedia.api.service;

import com.homepedia.common.transaction.TransactionHeatPoint;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import static com.homepedia.api.config.CacheConfig.CACHE_STATS;

/**
 * Aggregates geocoded transactions into heatmap points for a given viewport.
 * Buckets coordinates to a small grid before averaging so even a viewport
 * sitting on Paris doesn't return tens of thousands of rows — the heatmap
 * kernel works best with at most a few thousand weighted points anyway.
 *
 * <p>
 * Filtering on {@code latitude IS NOT NULL} hits the partial bbox index created
 * by migration 010, so the lookup stays in the millisecond range regardless of
 * how many partitions are attached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionHeatPointService {

	/**
	 * Quantisation step in degrees. 0.001° ≈ 111 m at the equator (less further
	 * north). Tight enough that adjacent buckets blur into a continuous heat blob
	 * at Leaflet zoom 12+, loose enough to cap a Paris-sized viewport at a few
	 * thousand rows.
	 */
	private static final double GRID_STEP = 0.001;

	/**
	 * Hard ceiling on returned rows. The query orders by sample size so the
	 * truncated tail is dominated by low-count buckets that contribute little to
	 * the final visual.
	 */
	private static final int ROW_LIMIT = 5000;

	/**
	 * Reject viewports wider than this many degrees — anything bigger is dezoomed
	 * past the city level where geocoded points add nothing visible.
	 */
	@Value("${homepedia.heatpoints.max-bbox-degrees:5.0}")
	private double maxBboxDegrees;

	private final JdbcTemplate jdbcTemplate;

	public enum Metric {
		AVERAGE_PRICE, AVERAGE_PRICE_PER_SQM, TRANSACTION_COUNT,
	}

	@Cacheable(value = CACHE_STATS, key = "'heatpoints:' + #metric + ':' + T(java.lang.Math).round(#south * 100) + ':' + T(java.lang.Math).round(#west * 100) + ':' + T(java.lang.Math).round(#north * 100) + ':' + T(java.lang.Math).round(#east * 100)")
	public List<TransactionHeatPoint> heatPoints(double south, double west, double north, double east, Metric metric) {
		if (north <= south || east <= west)
			return List.of();
		if ((north - south) > maxBboxDegrees || (east - west) > maxBboxDegrees)
			return List.of();

		final String aggregateExpr = switch (metric) {
			case AVERAGE_PRICE -> "AVG(property_value)";
			case AVERAGE_PRICE_PER_SQM -> "AVG(property_value / NULLIF(built_surface, 0))";
			case TRANSACTION_COUNT -> "COUNT(*)::double precision";
		};
		// Filter on built_surface > 0 only when the metric needs it, so the
		// transaction-count heatmap still surfaces lots without a recorded
		// built area.
		final String surfaceFilter = (metric == Metric.AVERAGE_PRICE_PER_SQM) ? "AND built_surface > 0" : "";
		final String sql = """
				SELECT
				  ROUND((latitude / %f)::numeric, 0) * %f  AS lat_q,
				  ROUND((longitude / %f)::numeric, 0) * %f AS lng_q,
				  %s AS value,
				  COUNT(*) AS samples
				FROM transactions
				WHERE latitude IS NOT NULL
				  AND latitude BETWEEN ? AND ?
				  AND longitude BETWEEN ? AND ?
				  AND property_value > 0
				  %s
				GROUP BY lat_q, lng_q
				HAVING %s IS NOT NULL
				ORDER BY samples DESC
				LIMIT %d
				""".formatted(GRID_STEP, GRID_STEP, GRID_STEP, GRID_STEP, aggregateExpr, surfaceFilter, aggregateExpr,
				ROW_LIMIT);

		return jdbcTemplate.query(sql, ps -> {
			ps.setDouble(1, south);
			ps.setDouble(2, north);
			ps.setDouble(3, west);
			ps.setDouble(4, east);
		}, (rs, i) -> new TransactionHeatPoint(rs.getDouble("lat_q"), rs.getDouble("lng_q"), rs.getDouble("value")));
	}
}
