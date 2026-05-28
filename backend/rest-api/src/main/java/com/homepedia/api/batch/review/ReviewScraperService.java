package com.homepedia.api.batch.review;

import com.homepedia.common.city.CityRepository;
import com.homepedia.common.review.CityReview;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewScraperService {

	private static final int BATCH_SIZE = 4000;
	private static final int GENERATION_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());
	private static final int WRITE_THREADS = 2;
	// Sentinel placed on the queue when generation is finished so the writers
	// can drain whatever is left and exit cleanly.
	private static final List<CityReview> POISON_PILL = List.of();

	private static final String JOB_NAME = "reviewImportJob";

	private final CityRepository cityRepository;
	private final MongoTemplate mongoTemplate;
	private final ReviewDataGenerator reviewDataGenerator;
	private final SentimentAnalysisService sentimentAnalysisService;
	private final com.homepedia.api.batch.config.BatchPhaseTracker phaseTracker;

	// No @Transactional: cities are read once at the start, and review writes
	// go to MongoDB. Wrapping the whole loop in a JPA transaction kept a
	// Postgres connection idle-in-transaction holding a RowShareLock on
	// `cities`, which blocked DROP/ALTER on tables with FKs to cities
	// (e.g. partition swaps during DVF imports).
	public void importReviews() {
		// Projection (insee codes only) instead of full City entities —
		// the generator never touches lat/lng/name/FKs, and the full set
		// of ~35 k hydrated rows was costing ~250 MB of JPA heap pressure
		// on every import.
		final var inseeCodes = cityRepository.findAllInseeCodes();
		log.info("Generating reviews for {} cities (gen threads={}, write threads={})", inseeCodes.size(),
				GENERATION_THREADS, WRITE_THREADS);
		phaseTracker.set(JOB_NAME, "Génération + écriture des avis…");

		// Bounded queue acts as backpressure between the generator pool
		// (CPU-bound: sentiment analysis is the dominant cost) and the
		// writer pool (IO-bound: round-trips to Mongo). Capacity is sized
		// so the writers stay fed but we never buffer the full ~350 k
		// reviews in RAM the way the previous "collect everything then
		// batch-insert" did.
		final BlockingQueue<List<CityReview>> queue = new LinkedBlockingQueue<>(GENERATION_THREADS * 4);
		final ExecutorService genPool = Executors.newFixedThreadPool(GENERATION_THREADS);
		final ExecutorService writePool = Executors.newFixedThreadPool(WRITE_THREADS);
		final AtomicInteger written = new AtomicInteger();
		final AtomicInteger lastLogged = new AtomicInteger();
		final var writers = new ArrayList<java.util.concurrent.Future<?>>(WRITE_THREADS);

		try {
			for (int i = 0; i < WRITE_THREADS; i++) {
				writers.add(writePool.submit(() -> drainAndWrite(queue, written, lastLogged)));
			}
			for (final var code : inseeCodes) {
				genPool.submit(() -> {
					try {
						final var reviews = generateForCity(code);
						if (!reviews.isEmpty()) {
							pushInBatches(queue, reviews);
						}
					} catch (InterruptedException e) {
						// Pool is being shut down. Restore the flag so the
						// worker thread exits, don't keep generating.
						Thread.currentThread().interrupt();
					} catch (RuntimeException e) {
						log.warn("review generation failed for {}: {}", code, e.getMessage());
					}
				});
			}
			genPool.shutdown();
			if (!genPool.awaitTermination(2, TimeUnit.HOURS)) {
				log.warn("generator pool didn't terminate within 2h — proceeding to drain anyway");
			}
			for (int i = 0; i < WRITE_THREADS; i++) {
				queue.put(POISON_PILL);
			}
			for (final var f : writers) {
				f.get();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("review import interrupted", e);
		} catch (java.util.concurrent.ExecutionException e) {
			throw new IllegalStateException("writer thread failed", e.getCause());
		} finally {
			writePool.shutdown();
		}

		log.info("Review import finished. Total reviews: {}", written.get());
	}

	private static void pushInBatches(final BlockingQueue<List<CityReview>> queue, final List<CityReview> reviews)
			throws InterruptedException {
		// Per-city payloads are small (a few dozen reviews); coalesce them
		// to BATCH_SIZE before handing off so the writers see Mongo-sized
		// batches instead of one InsertMany per city.
		for (int i = 0; i < reviews.size(); i += BATCH_SIZE) {
			queue.put(new ArrayList<>(reviews.subList(i, Math.min(i + BATCH_SIZE, reviews.size()))));
		}
	}

	private void drainAndWrite(final BlockingQueue<List<CityReview>> queue, final AtomicInteger written,
			final AtomicInteger lastLogged) {
		final var pending = new ArrayList<CityReview>(BATCH_SIZE);
		try {
			while (true) {
				final var next = queue.take();
				if (next == POISON_PILL) {
					if (!pending.isEmpty()) {
						mongoTemplate.insert(new ArrayList<>(pending), CityReview.class);
						bumpAndMaybeLog(pending.size(), written, lastLogged);
						pending.clear();
					}
					return;
				}
				pending.addAll(next);
				while (pending.size() >= BATCH_SIZE) {
					final var slice = new ArrayList<>(pending.subList(0, BATCH_SIZE));
					mongoTemplate.insert(slice, CityReview.class);
					pending.subList(0, BATCH_SIZE).clear();
					bumpAndMaybeLog(slice.size(), written, lastLogged);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void bumpAndMaybeLog(final int delta, final AtomicInteger written, final AtomicInteger lastLogged) {
		final int total = written.addAndGet(delta);
		final int last = lastLogged.get();
		// Throttle progress logs to one line per 50 k reviews so a long
		// import doesn't spam thousands of lines into the structured log.
		if (total - last >= 50_000 && lastLogged.compareAndSet(last, total)) {
			log.info("Saved {} reviews...", total);
		}
	}

	private List<CityReview> generateForCity(final String inseeCode) {
		final var generated = reviewDataGenerator.generateForCity(inseeCode);
		final var result = new ArrayList<CityReview>(generated.size());
		for (final var gen : generated) {
			final var sentiment = sentimentAnalysisService.analyze(gen.content());
			result.add(CityReview.builder().cityInseeCode(gen.cityInseeCode()).content(gen.content())
					.rating(gen.rating()).author(gen.author()).publishedAt(gen.publishedAt())
					.sentimentScore(sentiment.score()).sentimentLabel(sentiment.label()).build());
		}
		return result;
	}
}
