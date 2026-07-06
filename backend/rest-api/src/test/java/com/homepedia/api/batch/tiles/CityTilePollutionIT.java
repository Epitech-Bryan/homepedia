package com.homepedia.api.batch.tiles;

import static org.assertj.core.api.Assertions.assertThat;

import com.homepedia.api.TestcontainersConfig;
import com.homepedia.common.city.City;
import com.homepedia.common.city.CityRepository;
import com.homepedia.common.department.Department;
import com.homepedia.common.department.DepartmentRepository;
import com.homepedia.common.indicator.GeographicLevel;
import com.homepedia.common.indicator.Indicator;
import com.homepedia.common.indicator.IndicatorCategory;
import com.homepedia.common.indicator.IndicatorRepository;
import com.homepedia.common.region.Region;
import com.homepedia.common.region.RegionRepository;
import com.homepedia.common.stats.StatsRepository;
import java.util.stream.Collectors;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class CityTilePollutionIT {

	@Autowired
	private CityTileStatsRepository cityTileStatsRepository;

	@Autowired
	private IndicatorRepository indicatorRepository;

	@Autowired
	private RegionRepository regionRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private CityRepository cityRepository;

	@Autowired
	private StatsRepository statsRepository;

	@BeforeEach
	void seed() {
		indicatorRepository.deleteAll();
		cityRepository.deleteAll();
		departmentRepository.deleteAll();
		regionRepository.deleteAll();

		final var region = regionRepository.save(Region.builder().code("R1").name("Test Region").build());
		final var dept = departmentRepository
				.save(Department.builder().code("D1").name("Test Dept").region(region).build());
		cityRepository.save(City.builder().inseeCode("C1").name("Clean town").department(dept).build());
		cityRepository.save(City.builder().inseeCode("C2").name("No-GES town").department(dept).build());
		cityRepository.save(City.builder().inseeCode("C3").name("Dirty town").department(dept).build());

		ges("C1", "A", 100.0);
		ges("C3", "G", 100.0);
	}

	private void ges(final String code, final String letter, final double value) {
		indicatorRepository.save(Indicator.builder().geographicLevel(GeographicLevel.CITY).geographicCode(code)
				.category(IndicatorCategory.ENVIRONMENT).label("GES label " + letter).value(value).build());
	}

	@Test
	void cityWithoutGes_inheritsDepartmentAverage() {
		final var byCode = cityTileStatsRepository.findAllForTiles().stream()
				.collect(Collectors.toMap(CityTileStatsRepository.CityTileStatsProjection::getCode,
						CityTileStatsRepository.CityTileStatsProjection::getPollutionScore));

		assertThat(byCode.get("C1")).isCloseTo(1.0, Offset.offset(1e-6));
		assertThat(byCode.get("C3")).isCloseTo(7.0, Offset.offset(1e-6));
		assertThat(byCode.get("C2")).isCloseTo(4.0, Offset.offset(1e-6));
	}

	@Test
	void nationalGesAverage_isWeightedAcrossCommunes() {
		assertThat(statsRepository.aggregateCountryPollutionScore()).isCloseTo(4.0, Offset.offset(1e-6));
	}
}
