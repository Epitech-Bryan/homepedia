package com.homepedia.pipeline.insee;

import java.util.List;

public record InseeCommuneDto(String code, String nom, List<String> codesPostaux, String codeDepartement,
		Long population, Double surface, Centre centre) {

	public record Centre(String type, List<Double> coordinates) {
	}
}
