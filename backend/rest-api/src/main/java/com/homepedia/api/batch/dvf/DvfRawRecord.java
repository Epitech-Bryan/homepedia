package com.homepedia.api.batch.dvf;

import java.math.BigDecimal;
import org.apache.commons.lang3.StringUtils;

public record DvfRawRecord(String dateMutation, String natureMutation, String valeurFonciere, String noVoie,
		String codeVoie, String codePostal, String commune, String codeDepartement, String codeCommune, String section,
		String noPlan, String nombreDeLots, String typeLocal, String surfaceReelleBati, String nombrePiecesPrincipales,
		String surfaceTerrain, String typeVoie) {

	public BigDecimal parsedValeurFonciere() {
		if (StringUtils.isBlank(valeurFonciere)) {
			return null;
		}
		return new BigDecimal(valeurFonciere.replace(",", ".").replace("\"", "").trim());
	}

	public String fullInseeCode() {
		if (codeCommune == null) {
			return null;
		}
		String commune = codeCommune.trim();
		if (commune.length() >= 5) {
			return commune;
		}
		if (codeDepartement == null) {
			return null;
		}
		String dept = codeDepartement.trim();
		if (dept.length() == 1) {
			dept = "0" + dept;
		}
		while (commune.length() < 3) {
			commune = "0" + commune;
		}
		return dept + commune;
	}
}
