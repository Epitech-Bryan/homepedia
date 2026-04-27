package com.homepedia.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homepedia.common.city.City;
import com.homepedia.common.city.CityRepository;
import com.homepedia.common.department.Department;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CityServiceTest {

	@Mock
	private CityRepository cityRepository;

	@InjectMocks
	private CityService cityService;

	@Test
	void findAll_withQuery_searchesByName() {
		final var pageable = PageRequest.of(0, 10);
		final var city = buildCity("75056", "Paris");
		when(cityRepository.searchByName(eq("Paris"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(city)));

		final var result = cityService.findAll(null, "Paris", pageable);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().getFirst().name()).isEqualTo("Paris");
		verify(cityRepository).searchByName("Paris", pageable);
	}

	@Test
	void findAll_withDepartmentCode_filtersByDepartment() {
		final var pageable = PageRequest.of(0, 10);
		final var city = buildCity("75056", "Paris");
		when(cityRepository.findByDepartmentCode(eq("75"), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(city)));

		final var result = cityService.findAll("75", null, pageable);

		assertThat(result.getContent()).hasSize(1);
		verify(cityRepository).findByDepartmentCode("75", pageable);
	}

	@Test
	void findAll_noFilters_returnsAll() {
		final var pageable = PageRequest.of(0, 10);
		when(cityRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

		final var result = cityService.findAll(null, null, pageable);

		assertThat(result.getContent()).isEmpty();
		verify(cityRepository).findAll(pageable);
	}

	@Test
	void findAll_queryTakesPriorityOverDepartmentCode() {
		final var pageable = PageRequest.of(0, 10);
		final var city = buildCity("75056", "Paris");
		when(cityRepository.searchByName(eq("Paris"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(city)));

		final var result = cityService.findAll("75", "Paris", pageable);

		assertThat(result.getContent()).hasSize(1);
		verify(cityRepository).searchByName("Paris", pageable);
	}

	@Test
	void findByInseeCode_existing_returnsSummary() {
		final var city = buildCity("75056", "Paris");
		when(cityRepository.findByInseeCode("75056")).thenReturn(Optional.of(city));

		final var result = cityService.findByInseeCode("75056");

		assertThat(result).isPresent();
		assertThat(result.get().inseeCode()).isEqualTo("75056");
		assertThat(result.get().name()).isEqualTo("Paris");
	}

	@Test
	void findByInseeCode_notFound_returnsEmpty() {
		when(cityRepository.findByInseeCode("99999")).thenReturn(Optional.empty());

		final var result = cityService.findByInseeCode("99999");

		assertThat(result).isEmpty();
	}

	@Test
	void findAll_blankQuery_treatedAsNoQuery() {
		final var pageable = PageRequest.of(0, 10);
		final var city = buildCity("75056", "Paris");
		when(cityRepository.findByDepartmentCode(eq("75"), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(city)));

		final var result = cityService.findAll("75", "  ", pageable);

		assertThat(result.getContent()).hasSize(1);
		verify(cityRepository).findByDepartmentCode("75", pageable);
	}

	private City buildCity(final String inseeCode, final String name) {
		final var department = Department.builder().code("75").name("Paris").build();
		return City.builder().inseeCode(inseeCode).name(name).postalCode("75000").department(department)
				.population(2_161_000L).latitude(48.8566).longitude(2.3522).build();
	}
}
