package com.homepedia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.homepedia.api.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class RegionControllerIT {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void findAllRegions_returnsOkStatus() {
		final var response = restTemplate.getForEntity("/regions", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void findAllRegions_returnsJsonArray() {
		final var response = restTemplate.getForEntity("/regions", Object[].class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void findByCode_withUnknownCode_returnsNotFound() {
		final var response = restTemplate.getForEntity("/regions/{code}", String.class, "UNKNOWN");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
