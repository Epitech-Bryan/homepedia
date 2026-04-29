package com.homepedia.api.batch.insee;

import com.homepedia.common.city.City;
import com.homepedia.common.city.CityRepository;
import com.homepedia.common.department.Department;
import com.homepedia.common.department.DepartmentRepository;
import com.homepedia.common.region.Region;
import com.homepedia.common.region.RegionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InseeImportService {

	private static final int BATCH_SIZE = 1000;

	private final InseeApiClient inseeApiClient;
	private final RegionRepository regionRepository;
	private final DepartmentRepository departmentRepository;
	private final CityRepository cityRepository;

	// No @Transactional anywhere here. The whole importAll() runs ~20 min
	// (101 fetchCommunesForDepartment HTTP calls + ~35k city upserts), and
	// wrapping it in a JPA transaction kept a Postgres connection
	// idle-in-transaction holding a RowShareLock on cities/departments,
	// which blocked DROP/ALTER on FK-related tables (e.g. transactions_<year>
	// during DVF partition swap). The per-method @Transactional we used to
	// have on importRegions/importDepartments/importCommunes was also a
	// no-op when called from importAll(): Spring's @Transactional proxy is
	// bypassed on self-invocation. saveAll() opens its own short-lived tx
	// per batch via Spring Data, which is what we actually want.
	public void importAll() {
		importRegions();
		importDepartments();
		importCommunes();
	}

	public void importRegions() {
		final var dtos = inseeApiClient.fetchRegions();
		log.info("Fetched {} regions from INSEE API", CollectionUtils.emptyIfNull(dtos).size());

		final var existingRegions = regionRepository.findAll().stream()
				.collect(Collectors.toMap(Region::getCode, Function.identity()));

		final var regions = new ArrayList<Region>();
		for (final var dto : CollectionUtils.emptyIfNull(dtos)) {
			if (StringUtils.isBlank(dto.code()) || StringUtils.isBlank(dto.nom())) {
				continue;
			}
			final var existing = existingRegions.get(dto.code());
			if (existing != null) {
				existing.setName(dto.nom());
				regions.add(existing);
			} else {
				regions.add(Region.builder().code(dto.code()).name(dto.nom()).build());
			}
		}

		regionRepository.saveAll(regions);
		log.info("Imported {} regions", regions.size());
	}

	public void importDepartments() {
		final var dtos = inseeApiClient.fetchDepartments();
		log.info("Fetched {} departments from INSEE API", CollectionUtils.emptyIfNull(dtos).size());

		final var existingRegions = regionRepository.findAll().stream()
				.collect(Collectors.toMap(Region::getCode, Function.identity()));

		final var existingDepartments = departmentRepository.findAll().stream()
				.collect(Collectors.toMap(Department::getCode, Function.identity()));

		final var departments = new ArrayList<Department>();
		for (final var dto : CollectionUtils.emptyIfNull(dtos)) {
			if (StringUtils.isBlank(dto.code()) || StringUtils.isBlank(dto.nom())) {
				continue;
			}
			final var region = existingRegions.get(dto.codeRegion());
			if (region == null) {
				log.warn("Region {} not found for department {}", dto.codeRegion(), dto.code());
				continue;
			}
			final var existing = existingDepartments.get(dto.code());
			if (existing != null) {
				existing.setName(dto.nom());
				departments.add(existing);
			} else {
				departments.add(Department.builder().code(dto.code()).name(dto.nom()).region(region).build());
			}
		}

		departmentRepository.saveAll(departments);
		log.info("Imported {} departments", departments.size());
	}

	public void importCommunes() {
		final var existingDepartments = departmentRepository.findAll().stream()
				.collect(Collectors.toMap(Department::getCode, Function.identity()));

		final Map<String, City> existingCities = cityRepository.findAll().stream()
				.collect(Collectors.toMap(City::getInseeCode, Function.identity()));

		// Fetch communes for all departments in parallel. The previous
		// sequential loop spent most of its time waiting on HTTP I/O to
		// geo.api.gouv.fr (~100 calls × ~100-1000 ms each). 10 concurrent
		// threads divides the API-bound phase by ~10× while staying well
		// below typical public-API rate limits.
		final ExecutorService executor = Executors.newFixedThreadPool(10);
		final List<City> allCommunes;
		try {
			final List<CompletableFuture<List<City>>> futures = existingDepartments.values().stream()
					.map(department -> CompletableFuture.supplyAsync(
							() -> fetchAndMapCommunesForDepartment(department, existingCities), executor))
					.toList();
			allCommunes = futures.stream().map(CompletableFuture::join).flatMap(List::stream).toList();
		} finally {
			executor.shutdown();
		}

		// Save in fixed-size batches. Each saveAll() opens its own short
		// Spring Data transaction, so the cities lock is held only for the
		// duration of one batch insert, not the whole import.
		final var batch = new ArrayList<City>(BATCH_SIZE);
		var count = 0;
		for (final var city : allCommunes) {
			batch.add(city);
			if (batch.size() >= BATCH_SIZE) {
				cityRepository.saveAll(batch);
				count += batch.size();
				batch.clear();
				log.info("Imported {} communes...", count);
			}
		}
		if (!batch.isEmpty()) {
			cityRepository.saveAll(batch);
			count += batch.size();
		}

		log.info("Imported {} communes total", count);
		updateAggregateStats();
	}

	private List<City> fetchAndMapCommunesForDepartment(Department department, Map<String, City> existingCities) {
		final var dtos = CollectionUtils
				.emptyIfNull(inseeApiClient.fetchCommunesForDepartment(department.getCode()));
		log.info("Fetched {} communes for department {}", dtos.size(), department.getCode());
		final var result = new ArrayList<City>(dtos.size());
		for (final var dto : dtos) {
			if (StringUtils.isBlank(dto.code()) || StringUtils.isBlank(dto.nom())) {
				continue;
			}
			final var postalCode = CollectionUtils.emptyIfNull(dto.codesPostaux()).stream().findFirst().orElse(null);
			final var existing = existingCities.get(dto.code());
			final City city;
			if (existing != null) {
				existing.setName(dto.nom());
				existing.setPostalCode(postalCode);
				existing.setPopulation(dto.population());
				existing.setArea(dto.surface());
				setCoordinates(existing, dto);
				city = existing;
			} else {
				city = City.builder().inseeCode(dto.code()).name(dto.nom()).postalCode(postalCode)
						.department(department).population(dto.population()).area(dto.surface()).build();
				setCoordinates(city, dto);
			}
			result.add(city);
		}
		return result;
	}

	private void updateAggregateStats() {
		log.info("Recomputing aggregate population/area on departments and regions...");
		departmentRepository.recomputeAggregates();
		regionRepository.recomputeAggregates();
		log.info("Aggregate stats updated.");
	}

	private void setCoordinates(City city, InseeCommuneDto dto) {
		if (dto.centre() != null && dto.centre().coordinates() != null && dto.centre().coordinates().size() >= 2) {
			city.setLongitude(dto.centre().coordinates().get(0));
			city.setLatitude(dto.centre().coordinates().get(1));
		}
	}
}
