const BASE_URL = "/api";

async function fetchJson<T>(path: string, params?: Record<string, string>): Promise<T> {
  const url = new URL(`${BASE_URL}${path}`, window.location.origin);
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value) url.searchParams.set(key, value);
    });
  }
  const response = await fetch(url.toString());
  if (!response.ok) {
    throw new Error(`API error: ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

export const api = {
  regions: {
    list: () => fetchJson<RegionSummary[]>("/regions"),
    get: (code: string) => fetchJson<RegionSummary>(`/regions/${code}`),
  },
  departments: {
    list: (params?: Record<string, string>) =>
      fetchJson<DepartmentSummary[]>("/departments", params),
    get: (code: string) => fetchJson<DepartmentSummary>(`/departments/${code}`),
  },
  cities: {
    list: (params?: Record<string, string>) =>
      fetchJson<PagedResponse<CitySummary>>("/cities", params),
    get: (code: string) => fetchJson<CitySummary>(`/cities/${code}`),
  },
  transactions: {
    list: (params?: Record<string, string>) =>
      fetchJson<PagedResponse<TransactionSummary>>("/transactions", params),
    get: (id: number) => fetchJson<TransactionDetail>(`/transactions/${id}`),
    stats: (params?: Record<string, string>) =>
      fetchJson<TransactionStats>("/transactions/stats", params),
  },
  geo: {
    countries: () => fetchJson<GeoJSON.FeatureCollection>("/geo/countries"),
    regions: () => fetchJson<GeoJSON.FeatureCollection>("/geo/regions"),
    departments: (regionCode?: string) =>
      fetchJson<GeoJSON.FeatureCollection>(
        "/geo/departments",
        regionCode ? { regionCode } : undefined,
      ),
  },
  stats: {
    regions: () => fetchJson<RegionStats[]>("/stats/regions"),
    departments: (regionCode?: string) =>
      fetchJson<DepartmentStats[]>("/stats/departments", regionCode ? { regionCode } : undefined),
    departmentsPrecomputed: () => fetchJson<DepartmentDvfStats[]>("/stats/departments/precomputed"),
    departmentPrecomputed: (departmentCode: string) =>
      fetchJson<DepartmentDvfStats>(`/stats/departments/precomputed/${departmentCode}`),
    cities: async (codes: string[]): Promise<CityStats[]> => {
      const BATCH_SIZE = 200;
      if (codes.length <= BATCH_SIZE) {
        return fetchJson<CityStats[]>("/stats/cities", { codes: codes.join(",") });
      }
      const batches: Promise<CityStats[]>[] = [];
      for (let i = 0; i < codes.length; i += BATCH_SIZE) {
        batches.push(
          fetchJson<CityStats[]>("/stats/cities", {
            codes: codes.slice(i, i + BATCH_SIZE).join(","),
          }),
        );
      }
      return (await Promise.all(batches)).flat();
    },
  },
  indicators: {
    byLevelAndCode: (level: string, code: string, params?: Record<string, string>) =>
      fetchJson<IndicatorSummary[]>(`/indicators/${level}/${code}`, params),
  },
  reviews: {
    list: (inseeCode: string, params?: Record<string, string>) =>
      fetchJson<PagedResponse<ReviewSummary>>(`/cities/${inseeCode}/reviews`, params),
    wordCloud: (inseeCode: string) =>
      fetchJson<Record<string, number>>(`/cities/${inseeCode}/reviews/word-cloud`),
    sentimentStats: (inseeCode: string) =>
      fetchJson<SentimentStats>(`/cities/${inseeCode}/reviews/sentiment-stats`),
  },
};

// Types matching backend DTOs
export interface RegionSummary {
  code: string;
  name: string;
  population: number;
  area: number;
}

export interface DepartmentSummary {
  code: string;
  name: string;
  regionCode: string;
  population: number;
  area: number;
}

export interface RegionStats {
  code: string;
  name: string;
  population: number | null;
  area: number | null;
  transactionCount: number;
  averagePrice: number | null;
  averagePricePerSqm: number | null;
}

export interface DepartmentStats {
  code: string;
  name: string;
  regionCode: string;
  population: number | null;
  area: number | null;
  transactionCount: number;
  averagePrice: number | null;
  averagePricePerSqm: number | null;
}

export interface DepartmentDvfStats {
  departmentCode: string;
  transactionCount: number | null;
  avgPrice: number | null;
  avgPricePerSqm: number | null;
  medianPrice: number | null;
}

export interface CityStats {
  code: string;
  name: string;
  departmentCode: string;
  population: number | null;
  area: number | null;
  transactionCount: number;
  averagePrice: number | null;
  averagePricePerSqm: number | null;
}

export interface CitySummary {
  inseeCode: string;
  name: string;
  departmentCode: string;
  postalCode: string;
  population: number;
  area: number;
  latitude: number;
  longitude: number;
}

export interface TransactionSummary {
  id: number;
  mutationDate: string;
  propertyValue: number;
  propertyType: string;
  cityName: string;
  cityInseeCode: string;
  builtSurface: number;
  roomCount: number;
  landSurface: number;
}

export interface TransactionDetail {
  id: number;
  mutationDate: string;
  mutationNature: string;
  propertyValue: number;
  streetNumber: string | null;
  streetType: string | null;
  postalCode: string | null;
  cityName: string;
  cityInseeCode: string;
  departmentCode: string | null;
  propertyType: string;
  builtSurface: number;
  roomCount: number;
  landSurface: number;
  section: string | null;
  planNumber: string | null;
  lotCount: number | null;
  pricePerSqm: number | null;
}

export interface TransactionStats {
  totalTransactions: number;
  averagePrice: number;
  medianPrice: number;
  minPrice: number;
  maxPrice: number;
  averagePricePerSqm: number;
}

export interface IndicatorSummary {
  id: number;
  category: string;
  name: string;
  value: string;
  unit: string;
  year: number;
  source: string;
}

export interface ReviewSummary {
  id: string;
  content: string;
  sentimentScore: number;
  sentimentLabel: string;
  publishedAt: string;
  author: string;
  rating: number;
}

export interface SentimentStats {
  averageScore: number;
  positiveCount: number;
  negativeCount: number;
  neutralCount: number;
  totalReviews: number;
}

export interface PagedResponse<T> {
  _embedded?: Record<string, T[]>;
  page: {
    size: number;
    totalElements: number;
    totalPages: number;
    number: number;
  };
  _links?: Record<string, { href: string }>;
}
