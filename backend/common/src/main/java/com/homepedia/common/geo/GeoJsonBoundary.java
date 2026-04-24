package com.homepedia.common.geo;

import com.homepedia.common.indicator.GeographicLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "geo_boundaries", indexes = {
		@Index(name = "idx_geo_level_code", columnList = "geographic_level, geographic_code", unique = true)})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
public class GeoJsonBoundary {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GeographicLevel geographicLevel;

	@Column(nullable = false, length = 5)
	private String geographicCode;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "text", nullable = false)
	private String geometry;
}
