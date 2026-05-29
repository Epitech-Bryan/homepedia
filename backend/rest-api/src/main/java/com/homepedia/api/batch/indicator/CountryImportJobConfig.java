package com.homepedia.api.batch.indicator;

import com.homepedia.api.batch.tiles.WorldTileBuilder;
import com.homepedia.api.service.CountryGeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CountryImportJobConfig {

	private final CountryIndicatorImportService importService;

	private final CountryGeoService countryGeoService;

	private final ObjectProvider<WorldTileBuilder> worldTileBuilder;

	@Bean
	public Job countryImportJob(JobRepository jobRepository, Step countryImportStep) {
		return new JobBuilder("countryImportJob", jobRepository).start(countryImportStep).build();
	}

	@Bean
	public Step countryImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			final var count = importService.importAll();
			if (count > 0) {
				countryGeoService.refresh();
				final var tiles = worldTileBuilder.getIfAvailable();
				if (tiles != null) {
					tiles.rebuildAsync();
				}
			}
			log.info("Country import finished: {} indicators loaded", count);
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("countryImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
