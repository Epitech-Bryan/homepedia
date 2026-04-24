package com.homepedia.pipeline.insee;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class InseeApiClient {

	private final RestClient restClient;

	@Value("${homepedia.insee.api-url:https://geo.api.gouv.fr}")
	private String apiUrl;

	public List<InseeRegionDto> fetchRegions() {
		log.info("Fetching regions from {}", apiUrl);
		return restClient.get().uri(apiUrl + "/regions").retrieve().body(new ParameterizedTypeReference<>() {
		});
	}

	public List<InseeDepartmentDto> fetchDepartments() {
		log.info("Fetching departments from {}", apiUrl);
		return restClient.get().uri(apiUrl + "/departements?fields=nom,code,codeRegion").retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
	}

	public List<InseeCommuneDto> fetchCommunes() {
		log.info("Fetching communes from {}", apiUrl);
		return restClient.get()
				.uri(apiUrl
						+ "/communes?fields=nom,code,codesPostaux,codeDepartement,population,surface,centre&limit=0")
				.retrieve().body(new ParameterizedTypeReference<>() {
				});
	}
}
