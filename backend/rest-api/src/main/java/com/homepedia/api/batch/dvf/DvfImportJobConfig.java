package com.homepedia.api.batch.dvf;

import com.homepedia.api.batch.config.DatasetDownloadService;
import java.nio.file.Path;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * DVF import job. The {@code year} job parameter (or the
 * {@code homepedia.dvf.year} fallback property) drives both the source URL —
 * {@code https://files.data.gouv.fr/geo-dvf/latest/csv/{year}/full.csv.gz} —
 * and the target partition. The previous version was hardcoded to 2024.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DvfImportJobConfig {

	private static final String DOWNLOAD_URL_TEMPLATE = "https://files.data.gouv.fr/geo-dvf/latest/csv/%d/full.csv.gz";

	private final DvfImportService dvfImportService;
	private final DatasetDownloadService downloadService;

	@Value("${homepedia.dvf.zip-path:}")
	private String dvfZipPath;

	/**
	 * Default year used when no {@code year} job parameter is supplied. Falls back
	 * to last year so day-to-day reruns target the most recent fully published
	 * dataset (data.gouv.fr publishes year N around early year N+1).
	 */
	@Value("${homepedia.dvf.year:#{T(java.time.Year).now().getValue() - 1}}")
	private int defaultYear;

	@Bean
	public Job dvfImportJob(JobRepository jobRepository, Step dvfImportStep) {
		return new JobBuilder("dvfImportJob", jobRepository).start(dvfImportStep).build();
	}

	@Bean
	@JobScope
	public Step dvfImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			@Value("#{jobParameters['year']}") Long yearParam) {
		final int year = yearParam != null ? yearParam.intValue() : defaultYear;
		final Tasklet tasklet = (contribution, chunkContext) -> {
			log.info("DVF import targeting year {}", year);
			if (StringUtils.isNotBlank(dvfZipPath) && Path.of(dvfZipPath).toFile().exists()) {
				final var count = dvfImportService.importFromZip(year, Path.of(dvfZipPath));
				log.info("DVF import finished: {} transactions loaded for year {}", count, year);
				return RepeatStatus.FINISHED;
			}
			return importFromDownload(year);
		};
		return new StepBuilder("dvfImportStep", jobRepository).tasklet(tasklet, transactionManager).build();
	}

	private RepeatStatus importFromDownload(int year) throws Exception {
		validateYearBounds(year);
		final var url = DOWNLOAD_URL_TEMPLATE.formatted(year);
		// Always go through a temp file (with HTTP Range + retry) rather than a
		// raw streaming pipe. A connection drop mid-download used to require a
		// full restart of a 200 MB transfer; the resumable variant picks up
		// where it left off. Disk overhead is a few seconds on a SSD.
		final var suffix = url.endsWith(".zip") ? ".zip" : url.endsWith(".gz") ? ".csv.gz" : ".csv";
		Path tempFile = null;
		try {
			tempFile = downloadService.downloadResumable(url, "dvf-" + year + "-", suffix);
			final int count;
			if (url.endsWith(".zip")) {
				count = dvfImportService.importFromZip(year, tempFile);
			} else if (url.endsWith(".gz")) {
				count = dvfImportService.importFromGzip(year, tempFile);
			} else {
				count = dvfImportService.importFromCsv(year, tempFile);
			}
			log.info("DVF import from download finished: {} transactions loaded for year {}", count, year);
		} finally {
			downloadService.cleanup(tempFile);
		}
		return RepeatStatus.FINISHED;
	}

	private static void validateYearBounds(int year) {
		// Migration 005 only provisions partitions 2014..2030. Anything outside
		// that window would land in transactions_default and never get swapped.
		if (year < 2014 || year > 2030) {
			throw new IllegalArgumentException(
					"DVF year %d is outside the supported range [2014..2030]. Add a ".formatted(year)
							+ "partition migration first.");
		}
		// data.gouv.fr publishes a year around January of the next one; trying
		// to import the current year usually returns a 404.
		final int nowY = Year.now().getValue();
		if (year > nowY) {
			throw new IllegalArgumentException(
					"DVF year %d is in the future (current year is %d).".formatted(year, nowY));
		}
	}
}
