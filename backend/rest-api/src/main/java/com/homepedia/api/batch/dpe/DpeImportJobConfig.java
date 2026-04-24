package com.homepedia.api.batch.dpe;

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
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DpeImportJobConfig {

	private static final String DEFAULT_DOWNLOAD_URL = "https://data.ademe.fr/data-fair/api/v1/datasets/meg-83tjwtg8dyz4vv7h1dqe/lines?format=csv&size=10000&select=code_insee_ban,etiquette_dpe,etiquette_ges,annee_construction";

	private final DpeImportService dpeImportService;
	private final RestClient restClient;

	@Value("${homepedia.dpe.csv-path:}")
	private String csvPath;

	@Bean
	public Job dpeImportJob(JobRepository jobRepository, Step dpeImportStep) {
		return new JobBuilder("dpeImportJob", jobRepository).start(dpeImportStep).build();
	}

	@Bean
	public Step dpeImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			if (StringUtils.isNotBlank(csvPath) && Path.of(csvPath).toFile().exists()) {
				final var count = dpeImportService.importFromCsv(Path.of(csvPath));
				log.info("DPE import finished: {} indicators loaded", count);
				return RepeatStatus.FINISHED;
			}

			final var count = dpeImportService.importFromApi(DEFAULT_DOWNLOAD_URL, restClient);
			log.info("DPE import from API finished: {} indicators loaded", count);
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("dpeImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
