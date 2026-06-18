package com.homepedia.api.batch.tiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TileLayerBaker {

	@Value("${homepedia.tiles.tippecanoe-bin:tippecanoe}")
	private String tippecanoeBin;

	@Value("${homepedia.tiles.tile-join-bin:tile-join}")
	private String tileJoinBin;

	@Value("${homepedia.tiles.build-parallelism:2}")
	private int parallelism;

	public record LayerSpec(String name, Path source, int minZoom, int maxZoom, int simplification) {
	}

	/**
	 * Builds each layer into its own mbtiles in parallel (capped at
	 * {@code build-parallelism}), reusing a cached per-layer mbtiles when the
	 * source content and build flags are unchanged, then {@code tile-join}s them
	 * into {@code destination}. A cache miss or any doubt falls back to a full
	 * layer rebuild, so the output is always correct.
	 */
	public void bake(List<LayerSpec> layers, Path destination, Path cacheDir, List<String> commonArgs)
			throws IOException, InterruptedException {
		Files.createDirectories(cacheDir);
		final var fingerprint = String.join(" ", commonArgs);
		final var threads = Math.max(1, Math.min(parallelism, layers.size()));
		final var pool = Executors.newFixedThreadPool(threads);
		final var built = new ArrayList<Path>(layers.size());
		try {
			final var futures = new ArrayList<CompletableFuture<Path>>(layers.size());
			for (final var layer : layers) {
				futures.add(
						CompletableFuture.supplyAsync(() -> bakeLayer(layer, cacheDir, commonArgs, fingerprint), pool));
			}
			for (final var future : futures) {
				built.add(awaitLayer(future));
			}
		} finally {
			pool.shutdown();
		}
		tileJoin(built, destination);
	}

	private static Path awaitLayer(CompletableFuture<Path> future) throws IOException, InterruptedException {
		try {
			return future.join();
		} catch (CompletionException e) {
			final var cause = e.getCause();
			if (cause instanceof IOException io) {
				throw io;
			}
			if (cause instanceof InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw ie;
			}
			throw e;
		}
	}

	private Path bakeLayer(LayerSpec layer, Path cacheDir, List<String> commonArgs, String fingerprint) {
		final var out = cacheDir.resolve(layer.name() + ".mbtiles");
		final var stamp = cacheDir.resolve(layer.name() + ".stamp");
		try {
			final var key = sha256(layer.source()) + "|" + layer.minZoom() + "|" + layer.maxZoom() + "|"
					+ layer.simplification() + "|" + fingerprint;
			if (Files.exists(out) && Files.exists(stamp) && key.equals(Files.readString(stamp))) {
				log.info("tile cache hit for layer {} — reusing {}", layer.name(), out.getFileName());
				return out;
			}
			Files.deleteIfExists(stamp);
			runTippecanoe(layer, out, commonArgs);
			Files.writeString(stamp, key);
			return out;
		} catch (IOException e) {
			throw new CompletionException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CompletionException(e);
		}
	}

	private void runTippecanoe(LayerSpec layer, Path out, List<String> commonArgs)
			throws IOException, InterruptedException {
		final var args = new ArrayList<String>();
		args.add(tippecanoeBin);
		args.add("--output=" + out);
		args.add("--force");
		args.add("--minimum-zoom=" + layer.minZoom());
		args.add("--maximum-zoom=" + layer.maxZoom());
		args.addAll(commonArgs);
		args.add("-L");
		args.add(
				"{\"layer\":\"" + layer.name() + "\",\"file\":\"" + layer.source() + "\",\"minzoom\":" + layer.minZoom()
						+ ",\"maxzoom\":" + layer.maxZoom() + ",\"simplification\":" + layer.simplification() + "}");
		runProcess(args, "tippecanoe[" + layer.name() + "]");
	}

	private void tileJoin(List<Path> inputs, Path destination) throws IOException, InterruptedException {
		final var args = new ArrayList<String>();
		args.add(tileJoinBin);
		args.add("--force");
		args.add("--no-tile-size-limit");
		args.add("--output=" + destination);
		for (final var input : inputs) {
			args.add(input.toString());
		}
		runProcess(args, "tile-join");
	}

	private void runProcess(List<String> args, String tag) throws IOException, InterruptedException {
		final var pb = new ProcessBuilder(args);
		pb.redirectErrorStream(true);
		final var process = pb.start();
		try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				log.info("{}: {}", tag, line);
			}
		}
		final var exit = process.waitFor();
		if (exit != 0) {
			throw new IOException(tag + " exited " + exit);
		}
	}

	private static String sha256(Path file) throws IOException {
		try {
			final var digest = MessageDigest.getInstance("SHA-256");
			final var buffer = new byte[1 << 16];
			try (InputStream in = Files.newInputStream(file)) {
				int read;
				while ((read = in.read(buffer)) != -1) {
					digest.update(buffer, 0, read);
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 unavailable", e);
		}
	}
}
