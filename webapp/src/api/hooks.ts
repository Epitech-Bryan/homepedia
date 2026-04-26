import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";

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
    queryFn: async (): Promise<GeoJSON.FeatureCollection | null> => {
      if (!departmentCode) return null;
      const url = `https://geo.api.gouv.fr/departements/${departmentCode}/communes?fields=nom,code,population,surface&format=geojson&geometry=contour`;
      const res = await fetch(url);
      if (!res.ok) throw new Error(`Failed to fetch commune geojson: ${res.status}`);
      const raw = (await res.json()) as GeoJSON.FeatureCollection<
        GeoJSON.Geometry,
        { code: string; nom: string; population?: number; surface?: number }
      >;
      // Normalize property names to match the rest of our app (`name`, `code`).
      raw.features = raw.features.map((f) => ({
        ...f,
        properties: {
          ...f.properties,
          name: f.properties.nom,
        },
      })) as typeof raw.features;
      return raw;
    },
    enabled: !!departmentCode,
    staleTime: Infinity, // commune borders are stable
  });
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
