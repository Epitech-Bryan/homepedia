package com.homepedia.api.service;

import com.homepedia.api.mapper.DepartmentMapper;
import com.homepedia.common.department.DepartmentRepository;
import com.homepedia.common.department.DepartmentSummary;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

	private final DepartmentRepository departmentRepository;

	public List<DepartmentSummary> findAll(final String regionCode) {
		final var departments = StringUtils.isNotBlank(regionCode)
				? departmentRepository.findByRegionCode(regionCode)
				: departmentRepository.findAll();
		return DepartmentMapper.INSTANCE.convertToSummaryList(departments);
	}

	public Optional<DepartmentSummary> findByCode(final String code) {
		return departmentRepository.findByCode(code).map(DepartmentMapper.INSTANCE::convertToSummary);
	}
}
