package com.homepedia.common.geo.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record Feature(String type, Properties properties, @JsonRawValue String geometry) {

	public Feature(Properties properties, String geometry) {
		this("Feature", properties, geometry);
	}

}
