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
public class InfrastructureImportJobConfig {

	private final GenericIndicatorImportService importService;

	@Value("${homepedia.infrastructure.csv-path:}")
	private String csvPath;

	@Bean
	public Job infrastructureImportJob(JobRepository jobRepository, Step infrastructureImportStep) {
		return new JobBuilder("infrastructureImportJob", jobRepository).start(infrastructureImportStep).build();
	}

	@Bean
	public Step infrastructureImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			if (StringUtils.isBlank(csvPath) || !Path.of(csvPath).toFile().exists()) {
				log.warn("Infrastructure import skipped: no CSV at homepedia.infrastructure.csv-path");
				return RepeatStatus.FINISHED;
			}
			final var count = importService.importFromCsv(Path.of(csvPath), IndicatorCategory.INFRASTRUCTURE);
			log.info("Infrastructure import finished: {} indicators loaded", count);
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("infrastructureImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
