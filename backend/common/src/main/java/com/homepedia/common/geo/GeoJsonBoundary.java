package com.homepedia.common.geo;

import com.homepedia.common.indicator.GeographicLevel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "geo_boundaries", indexes = {
		@Index(name = "idx_geo_level_code", columnList = "geographic_level, geographic_code", unique = true)})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
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

	public GeoJsonBoundary(GeographicLevel geographicLevel, String geographicCode, String name, String geometry) {
		this.geographicLevel = geographicLevel;
		this.geographicCode = geographicCode;
		this.name = name;
		this.geometry = geometry;
	}
}
