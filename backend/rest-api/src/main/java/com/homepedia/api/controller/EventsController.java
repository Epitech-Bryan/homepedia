package com.homepedia.api.controller;

import com.homepedia.api.events.BatchEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Events", description = "Real-time server-sent events")
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventsController {

	private final BatchEventPublisher publisher;

	@Operation(summary = "Subscribe to batch import lifecycle events (SSE)")
	@GetMapping(value = "/batch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamBatchEvents() {
		return publisher.subscribe();
	}
}
