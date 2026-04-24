package com.homepedia.api.mapper;

import com.homepedia.common.geo.GeoJsonBoundary;
import com.homepedia.common.geo.dto.Feature;
import com.homepedia.common.geo.dto.FeatureCollection;
import com.homepedia.common.geo.dto.Properties;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface GeoMapper {

	GeoMapper INSTANCE = Mappers.getMapper(GeoMapper.class);

	@Mapping(target = "code", source = "geographicCode")
	@Mapping(target = "name", source = "name")
	@Mapping(target = "level", expression = "java(boundary.getGeographicLevel().name())")
	Properties convertToProperties(GeoJsonBoundary boundary);

	default Feature convertToFeature(GeoJsonBoundary boundary) {
		return new Feature(convertToProperties(boundary), boundary.getGeometry());
	}

	default FeatureCollection convertToFeatureCollection(List<GeoJsonBoundary> boundaries) {
		final var features = boundaries.stream().map(this::convertToFeature).toList();
		return new FeatureCollection(features);
	}

	default FeatureCollection emptyFeatureCollection() {
		return new FeatureCollection(List.of());
	}
}
