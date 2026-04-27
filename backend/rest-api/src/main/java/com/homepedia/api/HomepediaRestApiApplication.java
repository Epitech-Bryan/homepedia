package com.homepedia.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EntityScan(basePackages = "com.homepedia.common")
@EnableJpaRepositories(basePackages = "com.homepedia.common")
@EnableMongoRepositories(basePackages = "com.homepedia.common")
@EnableAsync
public class HomepediaRestApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HomepediaRestApiApplication.class, args);
	}
}
