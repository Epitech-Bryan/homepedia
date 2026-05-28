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
    priceHistory: (code: string) =>
      fetchJson<QuarterlyPricePoint[]>(`/cities/${code}/price-history`),
    irisIndicators: (code: string) =>
      fetchJson<IndicatorSummary[]>(`/cities/${code}/iris-indicators`),
  },
  transactions: {
    list: (params?: Record<string, string>) =>
      fetchJson<PagedResponse<TransactionSummary>>("/transactions", params),
    get: (id: number) => fetchJson<TransactionDetail>(`/transactions/${id}`),
    stats: (params?: Record<string, string>) =>
      fetchJson<TransactionStats>("/transactions/stats", params),
    heatPoints: (params: {
      south: number;
      west: number;
      north: number;
      east: number;
      metric?: "averagePrice" | "averagePricePerSqm" | "transactionCount";
    }) =>
      fetchJson<TransactionHeatPoint[]>("/transactions/heatpoints", {
        south: String(params.south),
        west: String(params.west),
        north: String(params.north),
        east: String(params.east),
        metric: params.metric ?? "averagePricePerSqm",
      }),
    markers: (params: {
      south: number;
      west: number;
      north: number;
      east: number;
      propertyType?: string;
      minPrice?: number;
      maxPrice?: number;
      limit?: number;
    }) => {
      const query: Record<string, string> = {
        south: String(params.south),
        west: String(params.west),
        north: String(params.north),
        east: String(params.east),
      };
      if (params.propertyType) query.propertyType = params.propertyType;
      if (params.minPrice !== undefined) query.minPrice = String(params.minPrice);
      if (params.maxPrice !== undefined) query.maxPrice = String(params.maxPrice);
      if (params.limit !== undefined) query.limit = String(params.limit);
      return fetchJson<TransactionMarker[]>("/transactions/markers", query);
    },
    comparableSales: (id: number) =>
      fetchJson<ComparableSale[]>(`/transactions/${id}/comparable-sales`),
  },
  geo: {
    countries: () => fetchJson<GeoJSON.FeatureCollection>("/geo/countries"),
    belgiumProvinces: () => fetchJson<GeoJSON.FeatureCollection>("/geo/belgium/provinces"),
    belgiumMunicipalities: () =>
      fetchJson<GeoJSON.FeatureCollection>("/geo/belgium/municipalities"),
    worldAdmin1: () => fetchJson<GeoJSON.FeatureCollection>("/geo/world/admin1"),
    worldAdmin2: () => fetchJson<GeoJSON.FeatureCollection>("/geo/world/admin2"),
    worldAdmin1Detail: (code: string) =>
      fetchJson<WorldAdmin1Detail>(`/geo/world/admin1/${encodeURIComponent(code)}`),
    worldSearch: (query: string, limit = 10) =>
      fetchJson<WorldSearchResult[]>("/geo/world/search", { q: query, limit: String(limit) }),
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
  tiles: {
    metricRanges: () =>
      fetchJson<Record<string, { min: number | null; max: number | null; breaks?: number[] }>>(
        "/tiles/cities/metric-ranges",
      ),
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

/**
 * Detailed view of one world admin-1 region — returned by
 * {@code GET /api/geo/world/admin1/{code}}. Mirrors {@code WorldAdmin1Detail}
 * on the backend.
 */
export interface WorldAdmin1Detail {
  code: string;
  name: string | null;
  country: string | null;
  population: number | null;
  area: number | null;
  gdpNominal: number | null;
  gdpPerCapita: number | null;
  /** [minX, minY, maxX, maxY] in WGS84 — used by the page to fly-to the region. */
  bbox: [number, number, number, number];
  admin2Count: number;
}

/** Row returned by the global search for a world admin-1 hit. */
export interface WorldSearchResult {
  code: string;
  name: string | null;
  country: string | null;
  population: number | null;
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
  /**
   * Weighted GES (greenhouse-gas) class on a 1..7 scale, computed from the
   * ADEME DPE feed: GES-A (≤ 5 kgCO₂eq/m²/yr) maps to 1, GES-G (> 80) maps
   * to 7. {@code null} when no GES rows exist for the commune.
   */
  pollutionScore: number | null;
}

export interface CitySummary {
  inseeCode: string;
  name: string;
  departmentCode: string;
  departmentName?: string;
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
  latitude: number | null;
  longitude: number | null;
}

export interface QuarterlyPricePoint {
  year: number;
  quarter: number;
  transactionCount: number;
  averagePricePerSqm: number | null;
}

export interface TransactionMarker {
  id: number;
  latitude: number;
  longitude: number;
  mutationDate: string;
  propertyValue: number;
  propertyType: string;
  builtSurface: number | null;
  roomCount: number | null;
}

export interface ComparableSale {
  similarityRank: number;
  comparableId: number;
  mutationDate: string;
  propertyValue: number;
  propertyType: string;
  builtSurface: number | null;
  roomCount: number | null;
  latitude: number;
  longitude: number;
  distanceM: number;
  priceDeltaPct: number;
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

export interface TransactionHeatPoint {
  latitude: number;
  longitude: number;
  value: number;
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
