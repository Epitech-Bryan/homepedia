package com.homepedia.api.batch.review;

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
public class ReviewImportJobConfig {

	private final ReviewScraperService reviewScraperService;

	@Bean
	public Job reviewImportJob(JobRepository jobRepository, Step reviewImportStep) {
		return new JobBuilder("reviewImportJob", jobRepository).start(reviewImportStep).build();
	}

	@Bean
	public Step reviewImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			reviewScraperService.importReviews();
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("reviewImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
