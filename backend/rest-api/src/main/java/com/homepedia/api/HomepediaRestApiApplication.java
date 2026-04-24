package com.homepedia.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.homepedia.common")
@EnableJpaRepositories(basePackages = "com.homepedia.common")
public class HomepediaRestApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HomepediaRestApiApplication.class, args);
	}
}
