package com.homepedia.api.batch.shared;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ParseUtils {

	public static Integer parseInteger(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static Double parseDouble(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		try {
			return Double.parseDouble(value.trim().replace(",", "."));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
