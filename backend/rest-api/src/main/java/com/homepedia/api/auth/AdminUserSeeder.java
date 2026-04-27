package com.homepedia.api.auth;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Seeds a default `admin` / `admin` account on first boot if the admins table
 * is empty. Project-school grade auth — no signup, no roles, anyone with an
 * account is admin.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminUserSeeder {

	private static final String DEFAULT_USERNAME = "admin";
	private static final String DEFAULT_PASSWORD = "admin";

	@Bean
	public ApplicationRunner adminUserSeederRunner(AdminUserRepository repository, PasswordEncoder passwordEncoder) {
		return args -> {
			if (repository.count() > 0) {
				return;
			}
			final var admin = AdminUser.builder().username(DEFAULT_USERNAME)
					.passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD)).createdAt(OffsetDateTime.now()).build();
			repository.save(admin);
			log.info("Seeded default admin account ({}/{})", DEFAULT_USERNAME, DEFAULT_PASSWORD);
		};
	}
}
