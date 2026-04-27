package com.homepedia.api.batch.dvf;

import com.homepedia.common.transaction.RealEstateTransaction;
import com.homepedia.common.transaction.TransactionRepository;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DvfBatchPersister {

	private static final String COPY_SQL = """
			COPY transactions (
			    mutation_date, mutation_nature, property_value, street_number, postal_code,
			    city_insee_code, section, plan_number, lot_count, property_type,
			    built_surface, room_count, land_surface, street_type
			) FROM STDIN
			""";

	private final TransactionRepository transactionRepository;
	private final DataSource dataSource;

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
		if (batch.isEmpty()) {
			return;
		}
		final Connection conn = DataSourceUtils.getConnection(dataSource);
		try {
			final var copyManager = new CopyManager(conn.unwrap(BaseConnection.class));
			final var payload = new StringBuilder(batch.size() * 256);
			for (var tx : batch) {
				appendRow(payload, tx);
			}
			try (var reader = new StringReader(payload.toString())) {
				copyManager.copyIn(COPY_SQL, reader);
			}
		} catch (SQLException | IOException e) {
			log.warn("COPY FROM STDIN failed, falling back to JPA saveAll: {}", e.getMessage());
			transactionRepository.saveAll(batch);
		} finally {
			DataSourceUtils.releaseConnection(conn, dataSource);
		}
	}

	private void appendRow(StringBuilder sb, RealEstateTransaction tx) {
		appendField(sb, tx.getMutationDate());
		sb.append('\t');
		appendField(sb, tx.getMutationNature());
		sb.append('\t');
		appendField(sb, tx.getPropertyValue());
		sb.append('\t');
		appendField(sb, tx.getStreetNumber());
		sb.append('\t');
		appendField(sb, tx.getPostalCode());
		sb.append('\t');
		appendField(sb, tx.getCity() != null ? tx.getCity().getInseeCode() : null);
		sb.append('\t');
		appendField(sb, tx.getSection());
		sb.append('\t');
		appendField(sb, tx.getPlanNumber());
		sb.append('\t');
		appendField(sb, tx.getLotCount());
		sb.append('\t');
		appendField(sb, tx.getPropertyType() != null ? tx.getPropertyType().name() : null);
		sb.append('\t');
		appendField(sb, tx.getBuiltSurface());
		sb.append('\t');
		appendField(sb, tx.getRoomCount());
		sb.append('\t');
		appendField(sb, tx.getLandSurface());
		sb.append('\t');
		appendField(sb, tx.getStreetType());
		sb.append('\n');
	}

	private void appendField(StringBuilder sb, Object value) {
		if (value == null) {
			sb.append("\\N");
			return;
		}
		final var s = value.toString();
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			switch (c) {
				case '\\' -> sb.append("\\\\");
				case '\t' -> sb.append("\\t");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				default -> sb.append(c);
			}
		}
	}
}
