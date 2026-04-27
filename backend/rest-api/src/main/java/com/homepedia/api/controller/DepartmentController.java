package com.homepedia.api.controller;

import static com.homepedia.api.constant.HomepediaConstant.RestPath.DEPARTMENTS;
import static com.homepedia.api.constant.HomepediaConstant.RestPath.Region.BY_CODE;

import com.homepedia.api.service.DepartmentService;
import com.homepedia.common.department.DepartmentSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Departments", description = "French administrative departments")
@RestController
@RequestMapping(DEPARTMENTS)
@RequiredArgsConstructor
public class DepartmentController {

	private final DepartmentService departmentService;

	@Operation(summary = "List departments", description = "List all departments, optionally filtered by region code")
	@GetMapping
	public ResponseEntity<List<DepartmentSummary>> findAll(
			@Parameter(description = "Region code to filter by") @RequestParam(required = false) final String regionCode) {
		return ResponseEntity.ok(departmentService.findAll(regionCode));
	}

	@Operation(summary = "Get a department by its code")
	@GetMapping(BY_CODE)
	public ResponseEntity<DepartmentSummary> findByCode(@PathVariable final String code) {
		return departmentService.findByCode(code).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}
}
