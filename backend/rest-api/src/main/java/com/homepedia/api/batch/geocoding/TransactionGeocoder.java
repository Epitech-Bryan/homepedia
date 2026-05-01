package com.homepedia.api.batch.geocoding;

import com.homepedia.api.batch.geocoding.BanGeocodingService.AddressInput;
import com.homepedia.api.batch.geocoding.BanGeocodingService.GeocodingResult;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pulls un-geocoded transactions from the partitioned {@code transactions}
 * table, ships them to {@link BanGeocodingService} and writes the resolved
 * coordinates back. Designed to be invoked from a Spring Batch tasklet so the
 * job can re-run on a cron without keeping the JVM busy when there's nothing to
 * do.
 *
 * <p>
 * Each invocation processes at most {@link #maxRowsPerRun} addresses so the BAN
 * endpoint isn't hammered and the job doesn't hold a DB connection for hours.
 * The partial index on {@code latitude IS NULL} keeps the backlog lookup cheap
 * regardless of how many rows have already been geocoded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionGeocoder {

	private static final String SELECT_BACKLOG = """
			SELECT t.id,
			       t.street_number,
			       t.street_type,
			       t.postal_code,
			       c.name AS city_name
			  FROM transactions t
			  LEFT JOIN cities c ON c.insee_code = t.city_insee_code
			 WHERE t.latitude IS NULL
			   AND t.postal_code IS NOT NULL
			   AND c.name IS NOT NULL
			 ORDER BY t.id
			 LIMIT ?
			""";

	private static final String UPDATE_COORDS = """
			UPDATE transactions
			   SET latitude = ?, longitude = ?, geocoded_at = ?
			 WHERE id = ?
			""";

	/**
	 * Marks an address as "geocoded but unresolved" so the next run doesn't keep
	 * retrying the same hopeless rows. Lat/lng stay NULL — only the timestamp gets
	 * bumped so the partial index drops the row from the backlog.
	 */
	private static final String MARK_UNRESOLVED = """
			UPDATE transactions
			   SET geocoded_at = ?
			 WHERE id = ?
			""";

	private final JdbcTemplate jdbcTemplate;
	private final BanGeocodingService geocoder;

	/**
	 * Max rows pulled per BAN call. BAN's CSV endpoint scales well past this; the
	 * soft cap matches the API's rate-limit guidance.
	 */
	@Value("${homepedia.geocoding.chunk-size:1000}")
	private int chunkSize;

	/**
	 * Hard cap per scheduled run so the job doesn't camp on the BAN API. Total
	 * geocode time = maxRowsPerRun / chunkSize * BAN round-trip.
	 */
	@Value("${homepedia.geocoding.max-rows-per-run:50000}")
	private int maxRowsPerRun;

	/**
	 * Backfill as many rows as the per-run cap allows. Returns the count of rows
	 * the BAN successfully resolved (lat/lng written).
	 */
	@Transactional
	public int runOnce() {
		int totalResolved = 0;
		int totalProcessed = 0;
		while (totalProcessed < maxRowsPerRun) {
			final int budget = Math.min(chunkSize, maxRowsPerRun - totalProcessed);
			final var addresses = nextBatch(budget);
			if (addresses.isEmpty()) {
				log.info("Geocoding backlog is empty — stopping at {} processed / {} resolved", totalProcessed,
						totalResolved);
				break;
			}
			final var results = geocoder.geocode(addresses);
			final int resolved = persist(results);
			totalResolved += resolved;
			totalProcessed += addresses.size();
			log.info("Geocoded chunk: {} addresses, {} resolved (running total {} / {})", addresses.size(), resolved,
					totalResolved, totalProcessed);
		}
		return totalResolved;
	}

	private List<AddressInput> nextBatch(int limit) {
		return jdbcTemplate.query(SELECT_BACKLOG, ps -> ps.setInt(1, limit),
				(rs, i) -> new AddressInput(rs.getLong("id"), rs.getString("street_number"),
						rs.getString("street_type"), rs.getString("postal_code"), rs.getString("city_name")));
	}

	private int persist(List<GeocodingResult> results) {
		if (results.isEmpty())
			return 0;
		final var now = Timestamp.from(Instant.now());
		final List<Object[]> updates = new ArrayList<>();
		final List<Object[]> markUnresolved = new ArrayList<>();
		int resolved = 0;
		for (var r : results) {
			if (r.hasCoords() && (r.score() == null || r.score() >= BanGeocodingService.MIN_KEEP_SCORE)) {
				updates.add(new Object[]{r.latitude(), r.longitude(), now, r.id()});
				resolved++;
			} else {
				markUnresolved.add(new Object[]{now, r.id()});
			}
		}
		if (!updates.isEmpty()) {
			jdbcTemplate.batchUpdate(UPDATE_COORDS, updates,
					new int[]{Types.DOUBLE, Types.DOUBLE, Types.TIMESTAMP, Types.BIGINT});
		}
		if (!markUnresolved.isEmpty()) {
			jdbcTemplate.batchUpdate(MARK_UNRESOLVED, markUnresolved, new int[]{Types.TIMESTAMP, Types.BIGINT});
		}
		return resolved;
	}

	/** Exposed for tests + admin endpoint that wants to know what's left. */
	@SuppressWarnings("DataFlowIssue")
	public long backlogSize() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM transactions WHERE latitude IS NULL AND geocoded_at IS NULL", Long.class);
	}
}
