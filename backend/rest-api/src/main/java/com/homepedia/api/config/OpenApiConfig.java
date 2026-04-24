package com.homepedia.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI homepediaOpenAPI() {
		return new OpenAPI()
				.info(new Info().title("Homepedia API").version("0.1.0")
						.description("REST API for the French housing market analysis platform")
						.contact(new Contact().name("Homepedia Team")).license(new License().name("MIT")))
				.servers(List.of(new Server().url("/").description("Default server")));
	}
}
