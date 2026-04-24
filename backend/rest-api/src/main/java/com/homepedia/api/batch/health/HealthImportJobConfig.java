package com.homepedia.api.batch.health;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class HealthImportJobConfig {

	private final HealthDataImportService healthDataImportService;

	@Value("${homepedia.health.csv-path:}")
	private String csvPath;

	@Bean
	public Job healthImportJob(JobRepository jobRepository, Step healthImportStep) {
		return new JobBuilder("healthImportJob", jobRepository).start(healthImportStep).build();
	}

	@Bean
	public Step healthImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			if (StringUtils.isBlank(csvPath)) {
				log.info("No health CSV path configured. Skipping.");
				return RepeatStatus.FINISHED;
			}
			final var path = Path.of(csvPath);
			if (!path.toFile().exists()) {
				log.warn("Health CSV file not found at {}. Skipping.", csvPath);
				return RepeatStatus.FINISHED;
			}
			final var count = healthDataImportService.importFromCsv(path);
			log.info("Health import finished: {} indicators loaded", count);
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("healthImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
