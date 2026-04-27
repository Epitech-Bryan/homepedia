package com.homepedia.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Auth", description = "Session-based authentication for the admin console")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	@Operation(summary = "Login", description = "Authenticates with username/password and creates a session cookie. Returns 200 on success, 401 on bad credentials.")
	@PostMapping("/login")
	public ResponseEntity<MeResponse> login(@RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		try {
			final Authentication auth = authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(body.username(), body.password()));
			final var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(auth);
			SecurityContextHolder.setContext(context);
			securityContextRepository.saveContext(context, request, response);
			return ResponseEntity.ok(new MeResponse(auth.getName()));
		} catch (BadCredentialsException e) {
			return ResponseEntity.status(401).build();
		}
	}

	@Operation(summary = "Logout", description = "Invalidates the session cookie.")
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request) {
		final var session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Current user", description = "Returns the authenticated username, or 401 if no session.")
	@GetMapping("/me")
	public ResponseEntity<MeResponse> me() {
		final var auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
			return ResponseEntity.status(401).build();
		}
		return ResponseEntity.ok(new MeResponse(auth.getName()));
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	public record MeResponse(String username) {
	}
}
