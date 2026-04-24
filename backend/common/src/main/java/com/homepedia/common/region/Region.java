package com.homepedia.common.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "regions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
public class Region {

	@Id
	@Column(length = 3)
	private final String code;

	@Column(nullable = false)
	private String name;

	private Long population;

	private Double area;

	public Region(String code, String name) {
		this.code = code;
		this.name = name;
	}
}
