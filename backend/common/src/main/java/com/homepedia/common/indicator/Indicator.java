package com.homepedia.common.indicator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "indicators", indexes = {
		@Index(name = "idx_indicator_geo", columnList = "geographic_level, geographic_code"),
		@Index(name = "idx_indicator_category", columnList = "category")})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Indicator {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GeographicLevel geographicLevel;

	@Column(nullable = false, length = 5)
	private String geographicCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private IndicatorCategory category;

	@Column(nullable = false)
	private String label;

	@Column(nullable = false)
	private Double value;

	private String unit;

	private Integer year;

	public Indicator(GeographicLevel geographicLevel, String geographicCode, IndicatorCategory category, String label,
			Double value, String unit, Integer year) {
		this.geographicLevel = geographicLevel;
		this.geographicCode = geographicCode;
		this.category = category;
		this.label = label;
		this.value = value;
		this.unit = unit;
		this.year = year;
	}
}
