package com.homepedia.api.events;

import java.time.Instant;

/**
 * Server-Sent Event payload for batch progress.
 *
 * @param type job lifecycle event ({@code STARTING}, {@code RUNNING},
 *             {@code COMPLETED}, {@code FAILED})
 * @param job  Spring Batch job name
 * @param message human-readable message
 * @param at server timestamp
 */
public record BatchEvent(String type, String job, String message, Instant at) {

	public static BatchEvent starting(String job, String message) {
		return new BatchEvent("STARTING", job, message, Instant.now());
	}

	public static BatchEvent running(String job, String message) {
		return new BatchEvent("RUNNING", job, message, Instant.now());
	}

	public static BatchEvent completed(String job, String message) {
		return new BatchEvent("COMPLETED", job, message, Instant.now());
	}

	public static BatchEvent failed(String job, String message) {
		return new BatchEvent("FAILED", job, message, Instant.now());
	}
}
