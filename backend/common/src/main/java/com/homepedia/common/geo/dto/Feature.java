package com.homepedia.common.geo.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record Feature(String type, Properties properties, JsonNode geometry) {

	public Feature(Properties properties, JsonNode geometry) {
		this("Feature", properties, geometry);
	}

}
