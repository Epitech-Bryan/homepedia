package com.homepedia.api.constant;

public interface HomepediaConstant {

	interface RestPath {

		String REGIONS = "/regions";
		String DEPARTMENTS = "/departments";
		String CITIES = "/cities";
		String TRANSACTIONS = "/transactions";
		String INDICATORS = "/indicators";
		String GEO = "/geo";
		String STATS = "/stats";

		interface Stats {
			String REGIONS = "/regions";
			String DEPARTMENTS = "/departments";
		}

		interface Region {
			String BY_CODE = "/{code}";
		}

		interface Department {
			String BY_CODE = "/{code}";
		}

		interface City {
			String BY_INSEE_CODE = "/{inseeCode}";
			String REVIEWS = CITIES + "/{inseeCode}/reviews";
			String WORD_CLOUD = REVIEWS + "/word-cloud";
			String SENTIMENT_STATS = REVIEWS + "/sentiment-stats";
		}

		interface Transaction {
			String STATS = "/stats";
		}

		interface Indicator {
			String BY_LEVEL_AND_CODE = "/{level}/{code}";
		}

		interface Geo {
			String GEO_REGIONS = "/regions";
			String GEO_DEPARTMENTS = "/departments";
		}
	}
}
