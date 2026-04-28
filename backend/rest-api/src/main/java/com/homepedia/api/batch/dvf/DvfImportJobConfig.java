package com.homepedia.api.batch.dvf;

import com.homepedia.api.batch.config.DatasetDownloadService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
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
	@StepScope
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
		// .zip needs random access (ZipInputStream needs the central directory), so
		// we still go through a temp file. Plain .csv / .csv.gz can be streamed
		// straight from the HTTP body into COPY — saves disk I/O and starts the
		// import as soon as the first bytes arrive.
		if (url.endsWith(".zip")) {
			return importZipViaTempFile(year, url);
		}
		final var isGzip = url.endsWith(".gz");
		final var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
		final var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(30)).GET().build();
		final var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() / 100 != 2) {
			response.body().close();
			throw new IllegalStateException(
					"Failed to download DVF year %d: HTTP %d".formatted(year, response.statusCode()));
		}
		try (var body = response.body()) {
			final int count = dvfImportService.importFromStream(year, body, isGzip);
			log.info("DVF import from streaming download finished: {} transactions loaded for year {}", count, year);
		}
		return RepeatStatus.FINISHED;
	}

	private RepeatStatus importZipViaTempFile(int year, String url) throws Exception {
		Path tempFile = null;
		try {
			tempFile = downloadService.downloadToTempFile(url, "dvf-" + year + "-", ".zip");
			final int count = dvfImportService.importFromZip(year, tempFile);
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
