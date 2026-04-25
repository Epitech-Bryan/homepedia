package com.homepedia.api.config;

import com.homepedia.common.shared.ErrorResponse;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AsyncRequestNotUsableException.class)
	public void handleClientDisconnect(AsyncRequestNotUsableException ex) {
		log.debug("Client disconnected before response was fully sent: {}", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		log.warn("Type mismatch on parameter '{}': {}", ex.getName(), ex.getMessage());
		final var error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request",
				"Invalid parameter: " + ex.getName(), Instant.now());
		return ResponseEntity.badRequest().body(error);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("Illegal argument: {}", ex.getMessage());
		final var error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(),
				Instant.now());
		return ResponseEntity.badRequest().body(error);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
		log.error("Unexpected error", ex);
		final var error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
				"An unexpected error occurred", Instant.now());
		return ResponseEntity.internalServerError().body(error);
	}
}
