package com.homepedia.api.batch.dvf;

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
public class DvfImportJobConfig {

	private static final String DEFAULT_DOWNLOAD_URL = "https://files.data.gouv.fr/geo-dvf/latest/csv/2024/full.csv.gz";

	private final DvfImportService dvfImportService;
	private final DatasetDownloadService downloadService;

	@Value("${homepedia.dvf.zip-path:}")
	private String dvfZipPath;

	@Bean
	public Job dvfImportJob(JobRepository jobRepository, Step dvfImportStep) {
		return new JobBuilder("dvfImportJob", jobRepository).start(dvfImportStep).build();
	}

	@Bean
	public Step dvfImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
		final Tasklet tasklet = (contribution, chunkContext) -> {
			if (StringUtils.isNotBlank(dvfZipPath) && Path.of(dvfZipPath).toFile().exists()) {
				final var count = dvfImportService.importFromZip(Path.of(dvfZipPath));
				log.info("DVF import finished: {} transactions loaded", count);
				return RepeatStatus.FINISHED;
			}

			return importFromDownload();
		};
		return new StepBuilder("dvfImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}

	private RepeatStatus importFromDownload() throws Exception {
		final var url = DEFAULT_DOWNLOAD_URL;
		final var isGzip = url.endsWith(".gz");
		final var suffix = isGzip ? ".csv.gz" : url.endsWith(".zip") ? ".zip" : ".csv";
		Path tempFile = null;
		try {
			tempFile = downloadService.downloadToTempFile(url, "dvf-", suffix);
			int count;
			if (url.endsWith(".zip")) {
				count = dvfImportService.importFromZip(tempFile);
			} else if (isGzip) {
				count = dvfImportService.importFromGzip(tempFile);
			} else {
				count = dvfImportService.importFromCsv(tempFile);
			}
			log.info("DVF import from download finished: {} transactions loaded", count);
		} finally {
			downloadService.cleanup(tempFile);
		}
		return RepeatStatus.FINISHED;
	}
}
