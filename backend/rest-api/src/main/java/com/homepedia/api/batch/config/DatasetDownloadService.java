package com.homepedia.api.batch.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetDownloadService {

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
