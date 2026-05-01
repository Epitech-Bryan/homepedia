package com.homepedia.api.batch.geocoding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch wrapper around {@link TransactionGeocoder}. Each run drains up
 * to {@code homepedia.geocoding.max-rows-per-run} addresses from the backlog,
 * then exits — the cron in
 * {@link com.homepedia.api.batch.config.BatchScheduler} brings the job back
 * later for the next slice.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GeocodingJobConfig {

	private final TransactionGeocoder geocoder;

	@Bean
	public Job geocodingJob(JobRepository jobRepository, Step geocodingStep) {
		return new JobBuilder("geocodingJob", jobRepository).start(geocodingStep).build();
	}

	@Bean
	public Step geocodingStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			final long backlog = geocoder.backlogSize();
			log.info("Geocoding job starting — backlog of {} un-geocoded transactions", backlog);
			if (backlog == 0) {
				return RepeatStatus.FINISHED;
			}
			final int resolved = geocoder.runOnce();
			log.info("Geocoding job finished — resolved {} addresses this run", resolved);
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("geocodingStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
