package com.homepedia.api.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Profile("local")
public class CorsConfig {

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		final var config = new CorsConfiguration();
		config.setAllowedOriginPatterns(List.of("*"));
		config.setAllowedMethods(List.of("*"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);

		final var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
