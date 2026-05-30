package com.homepedia.api.batch.tiles;

import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

/**
 * Serialises the heavy tile builds. CityTileBuilder and WorldTileBuilder each
 * guard against re-entrant runs of themselves, but nothing stopped a city build
 * and a world build from running at the same time — and both write large
 * intermediate GeoJSON plus tippecanoe scratch. Run together their peak disk
 * usage doubles, which is what tipped the pod over into "No space left on
 * device" (city enrich + world tippecanoe both failed at the same instant).
 *
 * <p>
 * A single fair permit forces the two pipelines to take turns, capping peak
 * disk at one build's footprint. Builds run on the async executor, so blocking
 * a thread here just queues the second build behind the first.
 */
@Component
public class TileBuildLock {

	private final Semaphore permit = new Semaphore(1, true);

	public void acquire() throws InterruptedException {
		permit.acquire();
	}

	public void release() {
		permit.release();
	}
}
