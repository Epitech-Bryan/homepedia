package com.homepedia.api.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Routes {@code @Transactional(readOnly = true)} traffic to the CNPG replica
 * service ({@code timescaledb-ro}) and everything else (writes, Liquibase,
 * Spring Batch, manual JdbcTemplate ops) to the primary
 * ({@code timescaledb-rw}). Lets us soak up the public read load without ever
 * blocking imports.
 *
 * <p>
 * If {@code spring.datasource.replica.url} is blank (dev / single-node) the
 * routing falls back to the primary for both routes — so the same image runs
 * locally without changes.
 *
 * <p>
 * Wrapped in a {@link LazyConnectionDataSourceProxy} because Spring/Hibernate
 * acquires a connection before the transaction-readonly flag is set; the lazy
 * proxy defers {@code getConnection()} until the first SQL statement, by which
 * time {@link TransactionSynchronizationManager#isCurrentTransactionReadOnly()}
 * is correct.
 */
@Slf4j
@Configuration
public class RoutingDataSourceConfig {

	enum Route {
		PRIMARY, REPLICA
	}

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource")
	public DataSourceProperties primaryDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@ConfigurationProperties("spring.datasource.replica")
	public DataSourceProperties replicaDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@ConfigurationProperties("spring.datasource.hikari")
	public HikariDataSource primaryDataSource(DataSourceProperties primaryDataSourceProperties) {
		return primaryDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
	}

	@Bean
	@ConfigurationProperties("spring.datasource.replica.hikari")
	public HikariDataSource replicaDataSource(DataSourceProperties replicaDataSourceProperties,
			HikariDataSource primaryDataSource, @Value("${spring.datasource.replica.url:}") String replicaUrl) {
		if (StringUtils.isBlank(replicaUrl)) {
			log.info("spring.datasource.replica.url is blank — read-only traffic stays on primary");
			return primaryDataSource;
		}
		log.info("Read-only traffic will route to {}", replicaUrl);
		return replicaDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
	}

	@Bean
	@Primary
	public DataSource dataSource(HikariDataSource primaryDataSource, HikariDataSource replicaDataSource) {
		final var routing = new AbstractRoutingDataSource() {
			@Override
			protected Object determineCurrentLookupKey() {
				return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? Route.REPLICA : Route.PRIMARY;
			}
		};
		routing.setTargetDataSources(Map.of(Route.PRIMARY, primaryDataSource, Route.REPLICA, replicaDataSource));
		routing.setDefaultTargetDataSource(primaryDataSource);
		routing.afterPropertiesSet();
		return new LazyConnectionDataSourceProxy(routing);
	}
}
