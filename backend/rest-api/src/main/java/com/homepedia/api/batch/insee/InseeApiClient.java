package com.homepedia.api.batch.insee;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * geo.api.gouv.fr is the upstream — public, generally fast, occasionally
 * 502/503 under load. Resilience4j wraps each call in:
 * <ul>
 * <li>{@code @Retry("geo-api")} — 3 attempts with exponential backoff on
 * IOException / 5xx (config in {@code application-prod.yml}).</li>
 * <li>{@code @CircuitBreaker("geo-api")} — opens after 60% failures over 20
 * calls, half-opens after 30 s. Stops the importCommunes parallel fan-out from
 * hammering a degraded API.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InseeApiClient {

	private final RestClient restClient;

	@Value("${homepedia.insee.api-url:https://geo.api.gouv.fr}")
	private String apiUrl;

	@CircuitBreaker(name = "geo-api")
	@Retry(name = "geo-api")
	public List<InseeRegionDto> fetchRegions() {
		log.info("Fetching regions from {}", apiUrl);
		return restClient.get().uri(apiUrl + "/regions").retrieve().body(new ParameterizedTypeReference<>() {
		});
	}

	@CircuitBreaker(name = "geo-api")
	@Retry(name = "geo-api")
	public List<InseeDepartmentDto> fetchDepartments() {
		log.info("Fetching departments from {}", apiUrl);
		return restClient.get().uri(apiUrl + "/departements?fields=nom,code,codeRegion").retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
	}

	@CircuitBreaker(name = "geo-api")
	@Retry(name = "geo-api")
	public List<InseeCommuneDto> fetchCommunesForDepartment(String departmentCode) {
		log.debug("Fetching communes for department {}", departmentCode);
		return restClient.get()
				.uri(apiUrl + "/departements/" + departmentCode
						+ "/communes?fields=nom,code,codesPostaux,codeDepartement,population,surface,centre")
				.retrieve().body(new ParameterizedTypeReference<>() {
				});
	}
}
