package com.homepedia.common.indicator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "indicators", indexes = {
		@Index(name = "idx_indicator_geo", columnList = "geographic_level, geographic_code"),
		@Index(name = "idx_indicator_category", columnList = "category")})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
public class Indicator {

	@Id
	@GeneratedValue(strategy = IDENTITY)
	private Long id;

	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private GeographicLevel geographicLevel;

	@Column(nullable = false, length = 5)
	private String geographicCode;

	@Enumerated(STRING)
	@Column(nullable = false, length = 30)
	private IndicatorCategory category;

	@Column(nullable = false)
	private String label;

	@Column(name = "indicator_value", nullable = false)
	private Double value;

	private String unit;

	private Integer year;
}
