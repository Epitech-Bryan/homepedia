package com.homepedia.api.batch.insee;

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
public class InseeImportJobConfig {

	private final InseeImportService inseeImportService;

	@Bean
	public Job inseeImportJob(JobRepository jobRepository, Step inseeImportStep) {
		return new JobBuilder("inseeImportJob", jobRepository).start(inseeImportStep).build();
	}

	@Bean
	public Step inseeImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			inseeImportService.importAll();
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("inseeImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
