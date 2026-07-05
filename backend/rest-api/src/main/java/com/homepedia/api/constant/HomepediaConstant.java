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
			String CITIES = "/cities";
			String COUNTRY = "/country";
		}

		/**
		 * Cross-level review aggregation endpoints (department / region / country).
		 * Mirror the per-city {@link City} review paths but roll the underlying commune
		 * reviews up to the requested scope.
		 */
		interface AreaReview {
			String REGION_REVIEWS = REGIONS + "/{code}/reviews";
			String REGION_WORD_CLOUD = REGION_REVIEWS + "/word-cloud";
			String REGION_SENTIMENT_STATS = REGION_REVIEWS + "/sentiment-stats";
			String DEPARTMENT_REVIEWS = DEPARTMENTS + "/{code}/reviews";
			String DEPARTMENT_WORD_CLOUD = DEPARTMENT_REVIEWS + "/word-cloud";
			String DEPARTMENT_SENTIMENT_STATS = DEPARTMENT_REVIEWS + "/sentiment-stats";
			String COUNTRY_REVIEWS = "/country/reviews";
			String COUNTRY_WORD_CLOUD = COUNTRY_REVIEWS + "/word-cloud";
			String COUNTRY_SENTIMENT_STATS = COUNTRY_REVIEWS + "/sentiment-stats";
		}

		interface Region {
			String BY_CODE = "/{code}";
		}

		interface Department {
			String BY_CODE = "/{code}";
		}

		interface City {
			String BY_INSEE_CODE = "/{inseeCode}";
			String PRICE_HISTORY = "/{inseeCode}/price-history";
			String IRIS_INDICATORS = "/{inseeCode}/iris-indicators";
			String REVIEWS = CITIES + "/{inseeCode}/reviews";
			String WORD_CLOUD = REVIEWS + "/word-cloud";
			String SENTIMENT_STATS = REVIEWS + "/sentiment-stats";
		}

		interface Transaction {
			String STATS = "/stats";
			String BY_ID = "/{id}";
			String HEATPOINTS = "/heatpoints";
			String MARKERS = "/markers";
			String COMPARABLE_SALES = "/{id}/comparable-sales";
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
