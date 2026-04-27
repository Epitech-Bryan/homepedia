package com.homepedia.api.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homepedia.common.geo.GeoJsonBoundary;
import com.homepedia.common.geo.dto.Feature;
import com.homepedia.common.geo.dto.FeatureCollection;
import com.homepedia.common.geo.dto.Properties;
import java.io.IOException;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class GeoMapper {

	@Autowired
	protected ObjectMapper objectMapper;

	@Mapping(target = "code", source = "geographicCode")
	@Mapping(target = "name", source = "name")
	@Mapping(target = "level", expression = "java(boundary.getGeographicLevel().name())")
	public abstract Properties convertToProperties(GeoJsonBoundary boundary);

	public Feature convertToFeature(GeoJsonBoundary boundary) {
		return new Feature(convertToProperties(boundary), parseGeometry(boundary.getGeometry()));
	}

	public FeatureCollection convertToFeatureCollection(List<GeoJsonBoundary> boundaries) {
		final var features = boundaries.stream().map(this::convertToFeature).toList();
		return new FeatureCollection(features);
	}

	public FeatureCollection emptyFeatureCollection() {
		return new FeatureCollection(List.of());
	}

	private JsonNode parseGeometry(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(raw);
		} catch (IOException e) {
			throw new IllegalStateException("Invalid geometry JSON: " + raw, e);
		}
	}
}
