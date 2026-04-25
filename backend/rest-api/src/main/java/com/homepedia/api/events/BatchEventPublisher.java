package com.homepedia.api.events;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory fan-out of batch lifecycle events to all connected SSE clients.
 * Subscribers register through {@link #subscribe()} and receive a heartbeat
 * plus every {@link BatchEvent} broadcast via {@link #publish(BatchEvent)}.
 *
 * <p>This is a deliberately simple implementation — fine for a single-pod
 * deployment. For multi-pod, a Redis pub/sub or a message broker would be
 * needed to share the event stream across instances.
 */
@Slf4j
@Component
public class BatchEventPublisher {

	private static final long EMITTER_TIMEOUT_MS = 60L * 60L * 1000L;

	private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	public SseEmitter subscribe() {
		final var emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
		emitters.add(emitter);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(e -> emitters.remove(emitter));
		try {
			emitter.send(SseEmitter.event().name("hello").data("connected"));
		} catch (IOException e) {
			emitters.remove(emitter);
		}
		log.debug("SSE subscriber connected; total = {}", emitters.size());
		return emitter;
	}

	public void publish(BatchEvent event) {
		log.debug("Publishing SSE event {}", event);
		for (final var emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name("batch").data(event));
			} catch (Exception e) {
				emitters.remove(emitter);
			}
		}
	}
}
