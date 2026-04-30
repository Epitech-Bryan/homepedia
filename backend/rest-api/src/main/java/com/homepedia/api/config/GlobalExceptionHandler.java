package com.homepedia.api.config;

import com.homepedia.api.admin.JobAlreadyRunningException;
import com.homepedia.api.admin.UnknownJobException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception that escapes a controller to RFC 7807
 * {@code application/problem+json}. Spring serializes the {@link ProblemDetail}
 * automatically and includes {@code type}, {@code title}, {@code status},
 * {@code detail}, {@code instance}, plus any extra properties we set.
 *
 * <p>
 * Bean-validation failures ({@link ConstraintViolationException} on
 * {@code @RequestParam}/{@code @PathVariable} bounds and
 * {@link MethodArgumentNotValidException} on {@code @RequestBody}) are mapped
 * to 400 with a {@code violations} array so the client can highlight invalid
 * fields. {@link ResponseStatusException} keeps its declared status (used by
 * services that want to throw 409/404 directly).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final URI TYPE_VALIDATION = URI.create("https://homepedia.bryan-ferrando.fr/problems/validation");
	private static final URI TYPE_NOT_FOUND = URI.create("https://homepedia.bryan-ferrando.fr/problems/not-found");
	private static final URI TYPE_CONFLICT = URI.create("https://homepedia.bryan-ferrando.fr/problems/conflict");
	private static final URI TYPE_INTERNAL = URI.create("https://homepedia.bryan-ferrando.fr/problems/internal");

	@ExceptionHandler(AsyncRequestNotUsableException.class)
	public void handleClientDisconnect(AsyncRequestNotUsableException ex) {
		log.debug("Client disconnected before response was fully sent: {}", ex.getMessage());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ProblemDetail handleNotFound(NoResourceFoundException ex) {
		log.debug("No resource found: {}", ex.getResourcePath());
		return problem(HttpStatus.NOT_FOUND, TYPE_NOT_FOUND, "Resource not found",
				"Resource not found: " + ex.getResourcePath());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		log.warn("Type mismatch on parameter '{}': {}", ex.getName(), ex.getMessage());
		return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Invalid parameter type",
				"Parameter '" + ex.getName() + "' has invalid type");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("Illegal argument: {}", ex.getMessage());
		return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Bad request",
				ex.getMessage() != null ? ex.getMessage() : "Invalid argument");
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
		final var detail = ex.getConstraintViolations().stream().map(v -> v.getPropertyPath() + " " + v.getMessage())
				.collect(Collectors.joining("; "));
		final var problem = problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation failed", detail);
		problem.setProperty("violations",
				ex.getConstraintViolations().stream()
						.map(v -> java.util.Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
						.toList());
		return problem;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
		final var detail = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + " " + e.getDefaultMessage()).collect(Collectors.joining("; "));
		final var problem = problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation failed", detail);
		problem.setProperty("violations", ex.getBindingResult().getFieldErrors().stream()
				.map(e -> java.util.Map.of("field", e.getField(), "message", e.getDefaultMessage())).toList());
		return problem;
	}

	@ExceptionHandler(JobAlreadyRunningException.class)
	public ProblemDetail handleJobAlreadyRunning(JobAlreadyRunningException ex) {
		log.debug("Job already running: {}", ex.getMessage());
		return problem(HttpStatus.CONFLICT, TYPE_CONFLICT, "Job already running", ex.getMessage());
	}

	@ExceptionHandler(UnknownJobException.class)
	public ProblemDetail handleUnknownJob(UnknownJobException ex) {
		log.debug("Unknown job: {}", ex.getMessage());
		return problem(HttpStatus.NOT_FOUND, TYPE_NOT_FOUND, "Unknown job", ex.getMessage());
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
		final var status = HttpStatus.resolve(ex.getStatusCode().value());
		final var safe = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
		log.debug("ResponseStatusException {}: {}", safe, ex.getReason());
		return problem(safe, typeFor(safe), safe.getReasonPhrase(), ex.getReason());
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleGeneral(Exception ex) {
		log.error("Unexpected error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, TYPE_INTERNAL, "Internal Server Error",
				"An unexpected error occurred");
	}

	private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail) {
		final var p = ProblemDetail.forStatus(status);
		p.setType(type);
		p.setTitle(title);
		p.setDetail(detail);
		p.setProperty("timestamp", Instant.now());
		return p;
	}

	private static URI typeFor(HttpStatus status) {
		if (status.is4xxClientError()) {
			return status == HttpStatus.NOT_FOUND
					? TYPE_NOT_FOUND
					: status == HttpStatus.CONFLICT ? TYPE_CONFLICT : TYPE_VALIDATION;
		}
		return TYPE_INTERNAL;
	}
}
