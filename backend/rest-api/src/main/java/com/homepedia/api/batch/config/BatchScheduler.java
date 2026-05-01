package com.homepedia.api.batch.config;

import com.homepedia.api.admin.AdminJobsService;
import com.homepedia.api.events.BatchEvent;
import com.homepedia.api.events.BatchEventPublisher;
import com.homepedia.api.service.CacheInvalidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "homepedia.scheduler.enabled", havingValue = "true")
public class BatchScheduler {

	private static final String ZONE = "${homepedia.scheduler.zone:Europe/Paris}";

	private final JobLauncher jobLauncher;
	private final JobExplorer jobExplorer;
	private final BatchEventPublisher eventPublisher;
	private final CacheInvalidationService cacheInvalidation;
	private final Job inseeImportJob;
	private final Job geoJsonImportJob;
	private final Job dvfImportJob;
	private final Job dpeImportJob;
	private final Job healthImportJob;
	private final Job reviewImportJob;
	private final Job economyImportJob;
	private final Job educationImportJob;
	private final Job environmentImportJob;
	private final Job infrastructureImportJob;
	private final Job geocodingJob;

	@Scheduled(cron = "${homepedia.scheduler.insee.cron:-}", zone = ZONE)
	public void runInseeImport() {
		runJob(inseeImportJob);
	}

	@Scheduled(cron = "${homepedia.scheduler.geo.cron:-}", zone = ZONE)
	public void runGeoJsonImport() {
		runJob(geoJsonImportJob);
	}

	@Scheduled(cron = "${homepedia.scheduler.dvf.cron:-}", zone = ZONE)
	public void runDvfImport() {
		runJob(dvfImportJob);
	}

	@Scheduled(cron = "${homepedia.scheduler.dpe.cron:-}", zone = ZONE)
	public void runDpeImport() {
		runJob(dpeImportJob);
	}

	@Scheduled(cron = "${homepedia.scheduler.health.cron:-}", zone = ZONE)
	public void runHealthImport() {
		runJob(healthImportJob);
	}

	@Scheduled(cron = "${homepedia.scheduler.reviews.cron:-}", zone = ZONE)
	public void runReviewImport() {
		runJob(reviewImportJob);
	}

	@Scheduled(cron = "${homepedia.scheduler.economy.cron:-}", zone = ZONE)
	public void runEconomyImport() {
		runJob(economyImportJob);
	}

	@Scheduled(cron = "${homepedia.scheduler.education.cron:-}", zone = ZONE)
	public void runEducationImport() {
		runJob(educationImportJob);
	}

	@Scheduled(cron = "${homepedia.scheduler.environment.cron:-}", zone = ZONE)
	public void runEnvironmentImport() {
		runJob(environmentImportJob);
	}

	@Scheduled(cron = "${homepedia.scheduler.infrastructure.cron:-}", zone = ZONE)
	public void runInfrastructureImport() {
		runJob(infrastructureImportJob);
	}

	@Scheduled(cron = "${homepedia.scheduler.geocoding.cron:-}", zone = ZONE)
	public void runGeocoding() {
		runJob(geocodingJob);
	}

	private void runJob(Job job) {
		final var name = job.getName();
		final var alreadyRunning = findRunningImportJobName();
		if (alreadyRunning != null) {
			log.warn("Skipping scheduled launch of {}: {} is already running", name, alreadyRunning);
			eventPublisher.publish(BatchEvent.failed(name, "skipped: " + alreadyRunning + " already running"));
			return;
		}
		final var start = System.currentTimeMillis();
		eventPublisher.publish(BatchEvent.starting(name, "Scheduled launch"));
		try {
			log.info("Scheduled launch of {}", name);
			final var params = new JobParametersBuilder().addLong("timestamp", start).toJobParameters();
			final var execution = jobLauncher.run(job, params);
			final var elapsed = System.currentTimeMillis() - start;
			log.info("Scheduled job {} finished with status {} in {} ms", name, execution.getStatus(), elapsed);
			eventPublisher.publish(BatchEvent.completed(name, execution.getStatus() + " in " + elapsed + " ms"));
			cacheInvalidation.evictGeoAndRefdataAndStats();
			cacheInvalidation.evictReviews();
		} catch (Exception e) {
			final var elapsed = System.currentTimeMillis() - start;
			log.error("Scheduled job {} failed after {} ms: {}", name, elapsed, e.getMessage(), e);
			eventPublisher.publish(BatchEvent.failed(name, e.getMessage()));
		}
	}

	private String findRunningImportJobName() {
		for (var beanName : AdminJobsService.JOB_NAMES.values()) {
			if (!jobExplorer.findRunningJobExecutions(beanName).isEmpty()) {
				return beanName;
			}
		}
		return null;
	}
}
