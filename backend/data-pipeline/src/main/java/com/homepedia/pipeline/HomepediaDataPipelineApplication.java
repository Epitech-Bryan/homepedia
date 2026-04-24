package com.homepedia.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.homepedia.common")
@EnableJpaRepositories(basePackages = "com.homepedia.common")
public class HomepediaDataPipelineApplication {

	public static void main(String[] args) {
		SpringApplication.run(HomepediaDataPipelineApplication.class, args);
	}
}
