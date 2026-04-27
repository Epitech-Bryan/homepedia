package com.homepedia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.homepedia.api.TestcontainersConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class TransactionControllerIT {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	@SuppressWarnings("unchecked")
	void search_returnsPagedHateoasResponse() {
		final var response = restTemplate.getForEntity("/transactions?page=0&size=5", Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();

		final var body = response.getBody();
		assertThat(body).containsKey("_links");
		assertThat(body).containsKey("page");

		final var page = (Map<String, Object>) body.get("page");
		assertThat(page).containsKey("size");
		assertThat(page).containsKey("totalElements");
		assertThat(page).containsKey("totalPages");
		assertThat(page).containsKey("number");
	}

	@Test
	void search_withDefaultPagination_returnsOk() {
		final var response = restTemplate.getForEntity("/transactions", Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void stats_returnsOk() {
		final var response = restTemplate.getForEntity("/transactions/stats", Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
}
