package com.homepedia.api.service;

import com.homepedia.api.events.BatchEvent;
import com.homepedia.api.events.BatchEventPublisher;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SparkJobLauncherService {

	private static final String JOB_NAME = "spark-dvf-aggregate";

	private final BatchEventPublisher eventPublisher;
	private final CacheInvalidationService cacheInvalidationService;

	@Value("${homepedia.spark.submit-command:}")
	private String submitCommand;

	@Value("${homepedia.spark.enabled:false}")
	private boolean sparkEnabled;

	public boolean isEnabled() {
		return sparkEnabled && StringUtils.isNotBlank(submitCommand);
	}

	@Async
	public CompletableFuture<Boolean> launchDvfAggregateJob() {
		if (!isEnabled()) {
			log.warn("Spark job launch requested but spark is not enabled or submit-command is not configured");
			eventPublisher.publish(BatchEvent.failed(JOB_NAME, "Spark not configured"));
			return CompletableFuture.completedFuture(false);
		}

		final var start = System.currentTimeMillis();
		eventPublisher.publish(BatchEvent.starting(JOB_NAME, "Launching DVF aggregate Spark job"));

		try {
			log.info("Launching Spark job with command: {}", submitCommand);
			final var process = new ProcessBuilder(List.of("sh", "-c", submitCommand)).redirectErrorStream(true)
					.start();

			eventPublisher.publish(BatchEvent.running(JOB_NAME, "Spark job running"));

			try (final var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				reader.lines().forEach(line -> log.info("[spark] {}", line));
			}

			final var exitCode = process.waitFor();
			final var elapsed = System.currentTimeMillis() - start;

			if (exitCode == 0) {
				log.info("Spark job completed successfully in {} ms", elapsed);
				eventPublisher.publish(BatchEvent.completed(JOB_NAME, "Completed in " + elapsed + " ms"));
				cacheInvalidationService.evictStats();
				return CompletableFuture.completedFuture(true);
			}

			log.error("Spark job failed with exit code {} after {} ms", exitCode, elapsed);
			eventPublisher.publish(BatchEvent.failed(JOB_NAME, "Exit code " + exitCode));
			return CompletableFuture.completedFuture(false);

		} catch (Exception e) {
			final var elapsed = System.currentTimeMillis() - start;
			log.error("Spark job failed after {} ms: {}", elapsed, e.getMessage(), e);
			eventPublisher.publish(BatchEvent.failed(JOB_NAME, e.getMessage()));
			return CompletableFuture.completedFuture(false);
		}
	}
}
