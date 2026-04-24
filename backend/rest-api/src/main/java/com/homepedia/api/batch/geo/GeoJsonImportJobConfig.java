package com.homepedia.api.batch.geo;

import lombok.RequiredArgsConstructor;
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

@Configuration
@RequiredArgsConstructor
public class GeoJsonImportJobConfig {

	private final GeoJsonImportService geoJsonImportService;

	@Bean
	public Job geoJsonImportJob(JobRepository jobRepository, Step geoJsonImportStep) {
		return new JobBuilder("geoJsonImportJob", jobRepository).start(geoJsonImportStep).build();
	}

	@Bean
	public Step geoJsonImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			geoJsonImportService.importAll();
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("geoJsonImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
