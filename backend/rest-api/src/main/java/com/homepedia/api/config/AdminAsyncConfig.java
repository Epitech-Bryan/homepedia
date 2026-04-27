package com.homepedia.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class AdminAsyncConfig {

	@Bean
	public TaskExecutor adminTaskExecutor() {
		final var executor = new SimpleAsyncTaskExecutor("admin-job-");
		executor.setConcurrencyLimit(4);
		return executor;
	}
}
