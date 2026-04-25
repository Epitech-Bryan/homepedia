package com.homepedia.api.batch.config;

import com.homepedia.api.events.BatchEvent;
import com.homepedia.api.events.BatchEventPublisher;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "homepedia.batch.startup-enabled", havingValue = "true")
public class BatchLauncherRunner implements CommandLineRunner {

	private final JobLauncher jobLauncher;
	private final BatchEventPublisher eventPublisher;
	private final Job inseeImportJob;
	private final Job dvfImportJob;
	private final Job geoJsonImportJob;
	private final Job reviewImportJob;
	private final Job healthImportJob;
	private final Job dpeImportJob;
	private final Job economyImportJob;
	private final Job educationImportJob;
	private final Job environmentImportJob;
	private final Job infrastructureImportJob;

	@Value("${homepedia.insee.import-enabled:false}")
	private boolean inseeEnabled;

	@Value("${homepedia.geo.import-enabled:false}")
	private boolean geoEnabled;

	@Value("${homepedia.dpe.import-enabled:false}")
	private boolean dpeEnabled;

	@Value("${homepedia.health.import-enabled:false}")
	private boolean healthEnabled;

	@Value("${homepedia.import.reviews.enabled:false}")
	private boolean reviewsEnabled;

	@Value("${homepedia.economy.import-enabled:false}")
	private boolean economyEnabled;

	@Value("${homepedia.education.import-enabled:false}")
	private boolean educationEnabled;

	@Value("${homepedia.environment.import-enabled:false}")
	private boolean environmentEnabled;

	@Value("${homepedia.infrastructure.import-enabled:false}")
	private boolean infrastructureEnabled;

	@Value("${homepedia.dvf.zip-path:}")
	private String dvfZipPath;

	@Override
	public void run(String... args) throws Exception {
		final var jobsToRun = new ArrayList<Job>();

		if (inseeEnabled) {
			jobsToRun.add(inseeImportJob);
		}
		if (geoEnabled) {
			jobsToRun.add(geoJsonImportJob);
		}
		if (org.apache.commons.lang3.StringUtils.isNotBlank(dvfZipPath)) {
			jobsToRun.add(dvfImportJob);
		}
		if (dpeEnabled) {
			jobsToRun.add(dpeImportJob);
		}
		if (healthEnabled) {
			jobsToRun.add(healthImportJob);
		}
		if (reviewsEnabled) {
			jobsToRun.add(reviewImportJob);
		}
		if (economyEnabled) {
			jobsToRun.add(economyImportJob);
		}
		if (educationEnabled) {
			jobsToRun.add(educationImportJob);
		}
		if (environmentEnabled) {
			jobsToRun.add(environmentImportJob);
		}
		if (infrastructureEnabled) {
			jobsToRun.add(infrastructureImportJob);
		}

		if (jobsToRun.isEmpty()) {
			log.info("No batch jobs enabled. Skipping batch execution.");
			return;
		}

		log.info("Starting {} batch job(s)...", jobsToRun.size());

		final var params = new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis())
				.toJobParameters();

		for (final var job : jobsToRun) {
			log.info("Launching job: {}", job.getName());
			eventPublisher.publish(BatchEvent.starting(job.getName(), "Boot-time launch"));
			try {
				final var execution = jobLauncher.run(job, params);
				log.info("Job {} completed", job.getName());
				eventPublisher.publish(BatchEvent.completed(job.getName(), execution.getStatus().toString()));
			} catch (Exception e) {
				log.error("Job {} failed: {}", job.getName(), e.getMessage(), e);
				eventPublisher.publish(BatchEvent.failed(job.getName(), e.getMessage()));
				throw e;
			}
		}

		log.info("All batch jobs finished.");
	}
}
