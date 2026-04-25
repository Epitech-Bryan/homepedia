package com.homepedia.api.batch.indicator;

import com.homepedia.common.indicator.IndicatorCategory;
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
public class EconomyImportJobConfig {

	private final GenericIndicatorImportService importService;

	@Value("${homepedia.economy.csv-path:}")
	private String csvPath;

	@Bean
	public Job economyImportJob(JobRepository jobRepository, Step economyImportStep) {
		return new JobBuilder("economyImportJob", jobRepository).start(economyImportStep).build();
	}

	@Bean
	public Step economyImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			if (StringUtils.isBlank(csvPath) || !Path.of(csvPath).toFile().exists()) {
				log.warn("Economy import skipped: no CSV at homepedia.economy.csv-path");
				return RepeatStatus.FINISHED;
			}
			final var count = importService.importFromCsv(Path.of(csvPath), IndicatorCategory.ECONOMY);
			log.info("Economy import finished: {} indicators loaded", count);
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("economyImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
