package com.homepedia.api.batch.dvf;

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
public class DvfImportJobConfig {

	private final DvfImportService dvfImportService;

	@Value("${homepedia.dvf.zip-path:}")
	private String dvfZipPath;

	@Bean
	public Job dvfImportJob(JobRepository jobRepository, Step dvfImportStep) {
		return new JobBuilder("dvfImportJob", jobRepository).start(dvfImportStep).build();
	}

	@Bean
	public Step dvfImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			if (StringUtils.isBlank(dvfZipPath)) {
				log.info("No DVF zip path configured. Skipping.");
				return RepeatStatus.FINISHED;
			}
			final var path = Path.of(dvfZipPath);
			if (!path.toFile().exists()) {
				log.warn("DVF zip file not found at {}. Skipping.", dvfZipPath);
				return RepeatStatus.FINISHED;
			}
			final var count = dvfImportService.importFromZip(path);
			log.info("DVF import finished: {} transactions loaded", count);
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("dvfImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
