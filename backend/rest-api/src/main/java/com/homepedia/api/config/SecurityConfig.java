package com.homepedia.api.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final CorsConfigurationSource corsConfigurationSource;

	public SecurityConfig(@Autowired(required = false) final CorsConfigurationSource corsConfigurationSource) {
		this.corsConfigurationSource = corsConfigurationSource;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
		if (corsConfigurationSource != null) {
			http.cors(cors -> cors.configurationSource(corsConfigurationSource));
		} else {
			http.cors(AbstractHttpConfigurer::disable);
		}

		return http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
	}
}
