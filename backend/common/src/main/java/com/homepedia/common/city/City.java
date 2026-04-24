package com.homepedia.common.city;

import com.homepedia.common.department.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cities")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
public class City {

	@Id
	@Column(length = 5)
	private final String inseeCode;

	@Column(nullable = false)
	private String name;

	@Column(length = 5)
	private String postalCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_code", nullable = false)
	private final Department department;

	private Long population;

	private Double area;

	private Double latitude;

	private Double longitude;

	public City(String inseeCode, String name, String postalCode, Department department) {
		this.inseeCode = inseeCode;
		this.name = name;
		this.postalCode = postalCode;
		this.department = department;
	}
}
