package com.homepedia.common.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "dept_dvf_stats")
@Getter
@Setter
@NoArgsConstructor(access = PROTECTED, force = true)
public class DepartmentDvfStats {

	@Id
	@Column(name = "department_code")
	private String departmentCode;

	private Long transactionCount;

	private Double avgPrice;

	private Double avgPricePerSqm;

	private Double medianPrice;
}
