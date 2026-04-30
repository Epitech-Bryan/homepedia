package com.homepedia.api.batch.dvf;

import com.homepedia.common.city.City;
import com.homepedia.common.city.CityRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CityCacheLoader {

	private final CityRepository cityRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Map<String, City> load() {
		final var all = cityRepository.findAll();
		final var map = new HashMap<String, City>(Math.max(16, (int) (all.size() / 0.75f) + 1));
		for (var c : all) {
			map.put(c.getInseeCode(), c);
		}
		log.info("Loaded {} cities into in-memory lookup cache", map.size());
		return map;
	}
}
