package com.homepedia.api.service;

import com.homepedia.api.mapper.CityMapper;
import com.homepedia.common.city.CityRepository;
import com.homepedia.common.city.CitySummary;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {

	private final CityRepository cityRepository;

	public Page<CitySummary> findAll(final String departmentCode, final String query, final Pageable pageable) {
		if (StringUtils.isNotBlank(query)) {
			return cityRepository.searchByName(query, pageable).map(CityMapper.INSTANCE::convertToSummary);
		}
		if (StringUtils.isNotBlank(departmentCode)) {
			return cityRepository.findByDepartmentCode(departmentCode, pageable)
					.map(CityMapper.INSTANCE::convertToSummary);
		}
		return cityRepository.findAll(pageable).map(CityMapper.INSTANCE::convertToSummary);
	}

	public Optional<CitySummary> findByInseeCode(final String inseeCode) {
		return cityRepository.findByInseeCode(inseeCode).map(CityMapper.INSTANCE::convertToSummary);
	}
}
