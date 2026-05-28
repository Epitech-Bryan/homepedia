package com.homepedia.api.batch.config;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory, best-effort tracker of the current phase of each running batch job
 * (keyed by the Spring Batch job bean name). Import services push short
 * human-readable labels ({@code "Téléchargement…"}, {@code "Écriture…"}) as
 * they progress; the admin status endpoint surfaces the value while the job is
 * running so the console can show what an import is doing right now.
 *
 * <p>
 * Intentionally not persisted and not multi-pod: it is a cosmetic progress
 * hint. A stale value left by a previous run is harmless because the status
 * view only returns it while the job is actually running, and the next run
 * overwrites it on its first {@link #set} call.
 */
@Component
public class BatchPhaseTracker {

	private final ConcurrentHashMap<String, String> phaseByJob = new ConcurrentHashMap<>();

	public void set(final String jobName, final String phase) {
		if (jobName == null) {
			return;
		}
		if (phase == null) {
			phaseByJob.remove(jobName);
		} else {
			phaseByJob.put(jobName, phase);
		}
	}

	public void clear(final String jobName) {
		if (jobName != null) {
			phaseByJob.remove(jobName);
		}
	}

	public String get(final String jobName) {
		return jobName == null ? null : phaseByJob.get(jobName);
	}
}
