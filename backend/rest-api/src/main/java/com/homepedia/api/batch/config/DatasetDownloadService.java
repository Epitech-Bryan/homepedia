package com.homepedia.api.batch.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetDownloadService {

	private static final int MAX_DOWNLOAD_ATTEMPTS = 5;
	private static final long INITIAL_BACKOFF_MS = 2_000L;
	private static final long MAX_BACKOFF_MS = 60_000L;

	private final RestClient restClient;

	public Path downloadToTempFile(String url, String prefix, String suffix) throws IOException {
		log.info("Downloading dataset from {} ...", url);
		final var tempFile = Files.createTempFile(prefix, suffix);

		restClient.get().uri(url).exchange((request, response) -> {
			try (final InputStream is = response.getBody(); final OutputStream os = Files.newOutputStream(tempFile)) {
				final var buffer = new byte[8192];
				var totalBytes = 0L;
				int bytesRead;
				while ((bytesRead = is.read(buffer)) != -1) {
					os.write(buffer, 0, bytesRead);
					totalBytes += bytesRead;
					if (totalBytes % (10 * 1024 * 1024) == 0) {
						log.info("Downloaded {} MB so far...", totalBytes / (1024 * 1024));
					}
				}
				log.info("Download complete: {} bytes written to {}", totalBytes, tempFile);
			}
			return null;
		});

		return tempFile;
	}

	/**
	 * Like {@link #downloadToTempFile} but retries on I/O errors with exponential
	 * backoff and resumes via HTTP {@code Range} when possible. data.gouv.fr
	 * supports range requests, so a connection drop at 80% of a 200 MB DVF gzip
	 * costs us only the remaining 40 MB instead of the whole file.
	 *
	 * <p>
	 * If the server replies {@code 200} to a {@code Range} request (some CDNs
	 * silently ignore it), we restart from offset 0 and overwrite the partial.
	 */
	public Path downloadResumable(String url, String prefix, String suffix) throws IOException {
		final var dest = Files.createTempFile(prefix, suffix);
		long offset = 0;
		IOException lastError = null;
		for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
			try {
				log.info("Download attempt {}/{} from {} (offset={})", attempt, MAX_DOWNLOAD_ATTEMPTS, url, offset);
				offset = fetchInto(url, dest, offset);
				log.info("Download complete: {} bytes written to {}", offset, dest);
				return dest;
			} catch (IOException e) {
				lastError = e;
				log.warn("Download attempt {} failed at offset {}: {}", attempt, offset, e.getMessage());
				if (attempt < MAX_DOWNLOAD_ATTEMPTS && !sleepBackoff(attempt)) {
					Files.deleteIfExists(dest);
					throw new IOException("Download interrupted", e);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Files.deleteIfExists(dest);
				throw new IOException("Download interrupted", e);
			}
		}
		Files.deleteIfExists(dest);
		throw new IOException("Download failed after " + MAX_DOWNLOAD_ATTEMPTS + " attempts: " + url, lastError);
	}

	private static long fetchInto(String url, Path dest, long offset) throws IOException, InterruptedException {
		final var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
		final var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(30)).GET();
		if (offset > 0) {
			builder.header("Range", "bytes=" + offset + "-");
		}
		final var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
		final int status = response.statusCode();
		if (status / 100 != 2) {
			response.body().close();
			throw new IOException("HTTP " + status + " from " + url);
		}
		// 200 response to a Range request means the server ignored it - we have
		// to overwrite from byte 0 to avoid a corrupted prefix.
		final boolean appending = offset > 0 && status == 206;
		long written = appending ? offset : 0;
		final var openOpts = appending
				? new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.APPEND}
				: new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING};
		try (final var is = response.body(); final var os = Files.newOutputStream(dest, openOpts)) {
			final var buffer = new byte[8192];
			int n;
			while ((n = is.read(buffer)) != -1) {
				os.write(buffer, 0, n);
				written += n;
			}
		}
		return written;
	}

	private static boolean sleepBackoff(int attempt) {
		final long delay = Math.min(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS * (1L << (attempt - 1)));
		try {
			Thread.sleep(delay);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	public void cleanup(Path tempFile) {
		if (tempFile == null) {
			return;
		}
		try {
			Files.deleteIfExists(tempFile);
			log.debug("Cleaned up temp file {}", tempFile);
		} catch (IOException e) {
			log.warn("Failed to clean up temp file {}: {}", tempFile, e.getMessage());
		}
	}
}
