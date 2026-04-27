import { useQueries, useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";

async function fetchCommuneGeojson(departmentCode: string): Promise<GeoJSON.FeatureCollection> {
  const url = `https://geo.api.gouv.fr/departements/${departmentCode}/communes?fields=nom,code,population,surface&format=geojson&geometry=contour`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Failed to fetch commune geojson: ${res.status}`);
  const raw = (await res.json()) as GeoJSON.FeatureCollection<
    GeoJSON.Geometry,
    { code: string; nom: string; population?: number; surface?: number }
  >;
  raw.features = raw.features.map((f) => ({
    ...f,
    properties: {
      ...f.properties,
      name: f.properties.nom,
    },
  })) as typeof raw.features;
  return raw;
}

async function fetchArrondissementsGeojson(
  communeCode: string,
): Promise<GeoJSON.FeatureCollection> {
  // geo.api.gouv.fr exposes municipal arrondissements only for Paris (75056),
  // Lyon (69123) and Marseille (13055). The endpoint returns FeatureCollection
  // with `nom`, `code` (75101..75120, etc.), `population`, `surface`.
  const url = `https://geo.api.gouv.fr/communes/${communeCode}/arrondissements-municipaux?fields=nom,code,population,surface&format=geojson&geometry=contour`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Failed to fetch arrondissements geojson: ${res.status}`);
  const raw = (await res.json()) as GeoJSON.FeatureCollection<
    GeoJSON.Geometry,
    { code: string; nom: string; population?: number; surface?: number }
  >;
  raw.features = raw.features.map((f) => ({
    ...f,
    properties: {
      ...f.properties,
      name: f.properties.nom,
    },
  })) as typeof raw.features;
  return raw;
}

export function useReviews(inseeCode: string, params?: Record<string, string>) {
  return useQuery({
    queryKey: ["reviews", inseeCode, params],
    queryFn: () => api.reviews.list(inseeCode, params),
    enabled: !!inseeCode,
  });
}

export function useWordCloud(inseeCode: string) {
  return useQuery({
    queryKey: ["wordCloud", inseeCode],
    queryFn: () => api.reviews.wordCloud(inseeCode),
    enabled: !!inseeCode,
  });
}

export function useSentimentStats(inseeCode: string) {
  return useQuery({
    queryKey: ["sentimentStats", inseeCode],
    queryFn: () => api.reviews.sentimentStats(inseeCode),
    enabled: !!inseeCode,
  });
}

export function useRegions() {
  return useQuery({ queryKey: ["regions"], queryFn: () => api.regions.list() });
}

export function useRegion(code: string) {
  return useQuery({
    queryKey: ["regions", code],
    queryFn: () => api.regions.get(code),
    enabled: !!code,
  });
}

export function useDepartments(params?: Record<string, string>) {
  return useQuery({
    queryKey: ["departments", params],
    queryFn: () => api.departments.list(params),
  });
}

export function useDepartment(code: string) {
  return useQuery({
    queryKey: ["departments", code],
    queryFn: () => api.departments.get(code),
    enabled: !!code,
  });
}

export function useCities(params?: Record<string, string>) {
  return useQuery({ queryKey: ["cities", params], queryFn: () => api.cities.list(params) });
}

export function useCitiesForDepartment(departmentCode?: string) {
  return useQuery({
    queryKey: ["cities", "byDepartment", departmentCode],
    queryFn: () => api.cities.list({ departmentCode: departmentCode ?? "", size: "1000" }),
    enabled: !!departmentCode,
  });
}

export function useCity(code: string) {
  return useQuery({
    queryKey: ["cities", code],
    queryFn: () => api.cities.get(code),
    enabled: !!code,
  });
}

export function useTransactions(params?: Record<string, string>) {
  return useQuery({
    queryKey: ["transactions", params],
    queryFn: () => api.transactions.list(params),
  });
}

export function useTransactionDetail(id: number | null) {
  return useQuery({
    queryKey: ["transactions", "detail", id],
    queryFn: () => {
      if (id === null) throw new Error("id is required");
      return api.transactions.get(id);
    },
    enabled: id !== null,
    staleTime: 5 * 60_000,
  });
}

export function useTransactionStats(params?: Record<string, string>) {
  return useQuery({
    queryKey: ["transactionStats", params],
    queryFn: () => api.transactions.stats(params),
  });
}

export function useGeoRegions() {
  return useQuery({ queryKey: ["geo", "regions"], queryFn: () => api.geo.regions() });
}

export function useGeoDepartments(regionCode?: string) {
  return useQuery({
    queryKey: ["geo", "departments", regionCode],
    queryFn: () => api.geo.departments(regionCode),
  });
}

export function useGeoCities(departmentCode?: string) {
  return useQuery({
    queryKey: ["geo", "cities", departmentCode],
    queryFn: () => {
      if (!departmentCode) throw new Error("departmentCode required");
      return fetchCommuneGeojson(departmentCode);
    },
    enabled: !!departmentCode,
    staleTime: Infinity, // commune borders are stable
  });
}

/**
 * Fetch commune polygons for several departments at once and merge them into
 * a single FeatureCollection. Each individual fetch is cached so panning
 * across the map only fires requests for newly visible departments.
 */
export function useGeoCitiesForDepartments(
  departmentCodes: string[],
): GeoJSON.FeatureCollection | null {
  const queries = useQueries({
    queries: departmentCodes.map((code) => ({
      queryKey: ["geo", "cities", code],
      queryFn: () => fetchCommuneGeojson(code),
      staleTime: Infinity,
    })),
  });
  const features: GeoJSON.Feature[] = [];
  for (const q of queries) {
    if (q.data?.features) features.push(...q.data.features);
  }
  if (features.length === 0) return null;
  return { type: "FeatureCollection", features };
}

export function useRegionStats() {
  return useQuery({ queryKey: ["stats", "regions"], queryFn: () => api.stats.regions() });
}

export function useDepartmentStats(regionCode?: string) {
  return useQuery({
    queryKey: ["stats", "departments", regionCode],
    queryFn: () => api.stats.departments(regionCode),
  });
}

export function useDepartmentPrecomputedStats(departmentCode?: string) {
  return useQuery({
    queryKey: ["stats", "departments", "precomputed", departmentCode],
    queryFn: () => api.stats.departmentPrecomputed(departmentCode as string),
    enabled: !!departmentCode,
  });
}

/**
 * Per-commune stats for the given INSEE codes. Codes are sorted before being
 * sent so panning the map within the same set hits the same TanStack Query
 * cache entry. Empty input short-circuits — no network call.
 */
export function useCityStats(codes: string[]) {
  const sorted = [...codes].sort();
  return useQuery({
    queryKey: ["stats", "cities", sorted],
    queryFn: () => api.stats.cities(sorted),
    enabled: sorted.length > 0,
    staleTime: 60_000,
  });
}

/**
 * Fetches arrondissements municipaux polygons in parallel for several parent
 * communes (only Paris/Lyon/Marseille have any) and merges them into a single
 * FeatureCollection.
 */
export function useArrondissementsForCities(
  communeCodes: string[],
): GeoJSON.FeatureCollection | null {
  const queries = useQueries({
    queries: communeCodes.map((code) => ({
      queryKey: ["geo", "arrondissements", code],
      queryFn: () => fetchArrondissementsGeojson(code),
      staleTime: Infinity,
    })),
  });
  const features: GeoJSON.Feature[] = [];
  for (const q of queries) {
    if (q.data?.features) features.push(...q.data.features);
  }
  if (features.length === 0) return null;
  return { type: "FeatureCollection", features };
}
