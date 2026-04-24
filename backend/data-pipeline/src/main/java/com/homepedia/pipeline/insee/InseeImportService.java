package com.homepedia.pipeline.insee;

import com.homepedia.common.city.City;
import com.homepedia.common.city.CityRepository;
import com.homepedia.common.department.Department;
import com.homepedia.common.department.DepartmentRepository;
import com.homepedia.common.region.Region;
import com.homepedia.common.region.RegionRepository;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InseeImportService {

	private static final int BATCH_SIZE = 1000;

	private final InseeApiClient inseeApiClient;
	private final RegionRepository regionRepository;
	private final DepartmentRepository departmentRepository;
	private final CityRepository cityRepository;

	@Transactional
	public void importAll() {
		importRegions();
		importDepartments();
		importCommunes();
	}

	@Transactional
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
				regions.add(new Region(dto.code(), dto.nom()));
			}
		}

		regionRepository.saveAll(regions);
		log.info("Imported {} regions", regions.size());
	}

	@Transactional
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
				departments.add(new Department(dto.code(), dto.nom(), region));
			}
		}

		departmentRepository.saveAll(departments);
		log.info("Imported {} departments", departments.size());
	}

	@Transactional
	public void importCommunes() {
		final var dtos = inseeApiClient.fetchCommunes();
		log.info("Fetched {} communes from INSEE API", CollectionUtils.emptyIfNull(dtos).size());

		final var existingDepartments = departmentRepository.findAll().stream()
				.collect(Collectors.toMap(Department::getCode, Function.identity()));

		final Map<String, City> existingCities = cityRepository.findAll().stream()
				.collect(Collectors.toMap(City::getInseeCode, Function.identity()));

		final var batch = new ArrayList<City>(BATCH_SIZE);
		var count = 0;

		for (final var dto : CollectionUtils.emptyIfNull(dtos)) {
			if (StringUtils.isBlank(dto.code()) || StringUtils.isBlank(dto.nom())) {
				continue;
			}
			final var department = existingDepartments.get(dto.codeDepartement());
			if (department == null) {
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
				city = new City(dto.code(), dto.nom(), postalCode, department);
				city.setPopulation(dto.population());
				city.setArea(dto.surface());
				setCoordinates(city, dto);
			}

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
	}

	private void setCoordinates(City city, InseeCommuneDto dto) {
		if (dto.centre() != null && dto.centre().coordinates() != null && dto.centre().coordinates().size() >= 2) {
			city.setLongitude(dto.centre().coordinates().get(0));
			city.setLatitude(dto.centre().coordinates().get(1));
		}
	}
}
