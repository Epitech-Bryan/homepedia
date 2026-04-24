package com.homepedia.api.batch.dpe;

import com.homepedia.api.batch.config.DatasetDownloadService;
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
public class DpeImportJobConfig {

	private final DpeImportService dpeImportService;
	private final DatasetDownloadService downloadService;

	@Value("${homepedia.dpe.csv-path:}")
	private String csvPath;

	@Value("${homepedia.dpe.download-url:}")
	private String downloadUrl;

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

			if (StringUtils.isNotBlank(downloadUrl)) {
				Path tempFile = null;
				try {
					tempFile = downloadService.downloadToTempFile(downloadUrl, "dpe-", ".csv");
					final var count = dpeImportService.importFromCsv(tempFile);
					log.info("DPE import from download finished: {} indicators loaded", count);
				} finally {
					downloadService.cleanup(tempFile);
				}
				return RepeatStatus.FINISHED;
			}

			log.info("No DPE CSV path or download URL configured. Skipping.");
			return RepeatStatus.FINISHED;
		};
		return new StepBuilder("dpeImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}
}
