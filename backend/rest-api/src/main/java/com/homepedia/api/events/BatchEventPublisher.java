package com.homepedia.api.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fan-out of batch lifecycle events to all connected SSE clients. Each pod
 * keeps its own list of {@link SseEmitter}s and relays events across pods via a
 * Redis pub/sub channel, so a client connected to pod A still sees jobs
 * triggered on pod B.
 *
 * <p>
 * To avoid double-broadcast, every outgoing message is tagged with this pod's
 * {@link #instanceId}; the listener drops echoes that come back from Redis. If
 * Redis is unreachable the publish path degrades to local-only fan-out — the
 * pod keeps working, multi-pod sync just pauses until Redis is back.
 */
@Slf4j
@Component
public class BatchEventPublisher implements MessageListener {

	static final String CHANNEL = "homepedia:batch-events";

	private static final long EMITTER_TIMEOUT_MS = 60L * 60L * 1000L;

	private final String instanceId = UUID.randomUUID().toString();
	private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public BatchEventPublisher(final StringRedisTemplate redisTemplate, final ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

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

	public void publish(final BatchEvent event) {
		log.debug("Publishing SSE event {}", event);
		fanOut(event);
		try {
			final var payload = objectMapper.writeValueAsString(new Envelope(instanceId, event));
			redisTemplate.convertAndSend(CHANNEL, payload);
		} catch (Exception e) {
			log.warn("Redis pub/sub broadcast failed; multi-pod sync skipped: {}", e.getMessage());
		}
	}

	@Override
	public void onMessage(final Message message, final byte[] pattern) {
		try {
			final var envelope = objectMapper.readValue(message.getBody(), Envelope.class);
			if (instanceId.equals(envelope.instanceId())) {
				return;
			}
			fanOut(envelope.event());
		} catch (Exception e) {
			log.warn("Failed to relay Redis pub/sub message: {}", e.getMessage());
		}
	}

	private void fanOut(final BatchEvent event) {
		for (final var emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name("batch").data(event));
			} catch (Exception e) {
				emitters.remove(emitter);
			}
		}
	}

	record Envelope(String instanceId, BatchEvent event) {
	}
}
