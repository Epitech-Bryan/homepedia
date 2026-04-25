package com.homepedia.api.batch.config;

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
	private final Job inseeImportJob;
	private final Job dvfImportJob;
	private final Job geoJsonImportJob;
	private final Job reviewImportJob;
	private final Job healthImportJob;
	private final Job dpeImportJob;

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

		if (jobsToRun.isEmpty()) {
			log.info("No batch jobs enabled. Skipping batch execution.");
			return;
		}

		log.info("Starting {} batch job(s)...", jobsToRun.size());

		final var params = new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis())
				.toJobParameters();

		for (final var job : jobsToRun) {
			log.info("Launching job: {}", job.getName());
			jobLauncher.run(job, params);
			log.info("Job {} completed", job.getName());
		}

		log.info("All batch jobs finished.");
	}
}
