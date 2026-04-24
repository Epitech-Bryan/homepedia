package com.homepedia.common.department;

import com.homepedia.common.region.Region;
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
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
public class Department {

	@Id
	@Column(length = 3)
	private final String code;

	@Column(nullable = false)
	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "region_code", nullable = false)
	private final Region region;

	private Long population;

	private Double area;

	public Department(String code, String name, Region region) {
		this.code = code;
		this.name = name;
		this.region = region;
	}
}
