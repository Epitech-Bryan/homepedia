import { useMemo } from "react";
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

// Department of each commune that publishes municipal arrondissements
// (Paris/Lyon/Marseille). Maps the parent-commune INSEE code to its
// department code so we can hit the /communes filter endpoint.
const ARRONDISSEMENT_PARENT_DEPT: Record<string, string> = {
  "75056": "75",
  "69123": "69",
  "13055": "13",
};

async function fetchArrondissementsGeojson(
  communeCode: string,
): Promise<GeoJSON.FeatureCollection> {
  // geo.api.gouv.fr dropped the /communes/{code}/arrondissements-municipaux
  // route in 2024 ; the supported way is now /communes filtered by
  // {@code type=arrondissement-municipal} on the department. Returns the
  // 20/9/16 arrondissement features with code = 75101..75120 / 69381..69389
  // / 13201..13216.
  const dept = ARRONDISSEMENT_PARENT_DEPT[communeCode];
  if (!dept) {
    return { type: "FeatureCollection", features: [] };
  }
  const url = `https://geo.api.gouv.fr/communes?codeDepartement=${dept}&type=arrondissement-municipal&fields=nom,code,population,surface&format=geojson&geometry=contour`;
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

/**
 * Aggregated reviews for a geographic scope above the commune level.
 * `basePath` is the scope root (`/regions/{code}`, `/departments/{code}` or
 * `/country`); pass `null` to disable the query (e.g. while the code param is
 * still empty).
 */
export function useAreaReviews(basePath: string | null, params?: Record<string, string>) {
  return useQuery({
    queryKey: ["areaReviews", basePath, params],
    queryFn: () => api.areaReviews.list(basePath as string, params),
    enabled: !!basePath,
  });
}

export function useAreaWordCloud(basePath: string | null) {
  return useQuery({
    queryKey: ["areaWordCloud", basePath],
    queryFn: () => api.areaReviews.wordCloud(basePath as string),
    enabled: !!basePath,
  });
}

export function useAreaSentiment(basePath: string | null) {
  return useQuery({
    queryKey: ["areaSentiment", basePath],
    queryFn: () => api.areaReviews.sentimentStats(basePath as string),
    enabled: !!basePath,
  });
}

export function useCountryStats() {
  return useQuery({
    queryKey: ["stats", "country"],
    queryFn: () => api.stats.country(),
    staleTime: 30 * 60_000,
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

/**
 * Autocomplete-flavoured city search — used by the header QuickSearch to
 * resolve a freeform name ("Lille") to its INSEE code. Skipped under 2
 * characters to keep the trigram GIN index from being abused; capped at 8
 * results to match the dropdown height.
 */
export function useCitySearch(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: ["cities", "search", trimmed.toLowerCase()],
    queryFn: () => api.cities.list({ query: trimmed, size: "8" }),
    enabled: trimmed.length >= 2,
    staleTime: 60_000,
  });
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

/**
 * Per-commune quarterly price/m² history (issue #9). The endpoint returns
 * one row per quarter from the {@code city_price_quarterly_stats} pre-agg,
 * cached server-side for 30 min; we add a short client-side staleTime so a
 * page refresh doesn't refire a request that won't have new data anyway.
 */
export function useCityPriceHistory(code: string) {
  return useQuery({
    queryKey: ["cities", code, "price-history"],
    queryFn: () => api.cities.priceHistory(code),
    enabled: !!code,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * IRIS-level indicators (median income, poverty rate, …) under a given
 * commune — fetched from the {@code /cities/{insee}/iris-indicators}
 * endpoint. Empty until the Filosofi import job has run, so the consumer
 * should treat an empty list as "data not yet available" rather than an
 * error. 12 h cache matches CACHE_REFDATA on the backend.
 */
export function useCityIrisIndicators(code: string) {
  return useQuery({
    queryKey: ["cities", code, "iris-indicators"],
    queryFn: () => api.cities.irisIndicators(code),
    enabled: !!code,
    staleTime: 12 * 60 * 60 * 1000,
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

/**
 * Pulls the per-bucket heatmap points the backend aggregates from geocoded
 * transactions. Disabled at world/regional zoom — at that scale the polygon
 * sampling fallback already paints the right shape and the bbox is too wide
 * for the endpoint anyway.
 */
export function useTransactionHeatPoints(params: {
  south: number;
  west: number;
  north: number;
  east: number;
  metric: "averagePrice" | "averagePricePerSqm" | "transactionCount";
  enabled: boolean;
}) {
  // Snap the bbox to a coarser grid so panning doesn't refire the query for
  // every pixel; matches the cache key precision on the backend.
  const snap = (n: number) => Math.round(n * 100) / 100;
  return useQuery({
    queryKey: [
      "transactionHeatPoints",
      params.metric,
      snap(params.south),
      snap(params.west),
      snap(params.north),
      snap(params.east),
    ],
    queryFn: () =>
      api.transactions.heatPoints({
        south: params.south,
        west: params.west,
        north: params.north,
        east: params.east,
        metric: params.metric,
      }),
    enabled: params.enabled,
    staleTime: 60_000,
  });
}

/**
 * Per-transaction markers inside the current viewport. Only enabled at
 * close-in zooms — the backend rejects bboxes wider than 0.2° anyway, so a
 * dezoomed map would just get empty arrays. The hook snaps the bbox to the
 * same 0.001° grid the backend uses for its cache key (~110 m).
 */
export function useTransactionMarkers(params: {
  south: number;
  west: number;
  north: number;
  east: number;
  propertyType?: string;
  minPrice?: number;
  maxPrice?: number;
  limit?: number;
  enabled: boolean;
}) {
  const snap = (n: number) => Math.round(n * 1000) / 1000;
  return useQuery({
    queryKey: [
      "transactionMarkers",
      snap(params.south),
      snap(params.west),
      snap(params.north),
      snap(params.east),
      params.propertyType ?? null,
      params.minPrice ?? null,
      params.maxPrice ?? null,
      params.limit ?? null,
    ],
    queryFn: () =>
      api.transactions.markers({
        south: params.south,
        west: params.west,
        north: params.north,
        east: params.east,
        propertyType: params.propertyType,
        minPrice: params.minPrice,
        maxPrice: params.maxPrice,
        limit: params.limit,
      }),
    enabled: params.enabled,
    staleTime: 60_000,
  });
}

export function useGeoCountries() {
  return useQuery({
    queryKey: ["geo", "countries"],
    queryFn: () => api.geo.countries(),
    // Country borders are essentially immutable. Cache for the lifetime of the
    // tab — a single ETag round-trip on revalidation is enough.
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

export function useGeoBelgiumProvinces() {
  return useQuery({
    queryKey: ["geo", "belgium", "provinces"],
    queryFn: () => api.geo.belgiumProvinces(),
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

export function useGeoBelgiumMunicipalities(enabled = true) {
  return useQuery({
    queryKey: ["geo", "belgium", "municipalities"],
    queryFn: () => api.geo.belgiumMunicipalities(),
    enabled,
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

export function useGeoWorldAdmin1() {
  return useQuery({
    queryKey: ["geo", "world", "admin1"],
    queryFn: () => api.geo.worldAdmin1(),
    // Static dataset (~38 countries × admin-1) — never changes for the
    // tab's lifetime. ~1.5 MB on the wire after Brotli, fetched once on
    // first navigation that hits zoom 5-7.
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

/**
 * GADM admin-2 boundaries (~6 k features over ~18 European countries) —
 * the level below admin-1. Lets the map render sub-region detail past
 * zoom 7 outside France. ~3.7 MB on the wire after Brotli, gated on the
 * `enabled` flag so we don't pay it on world / region zoom where the
 * detail is unreadable anyway.
 */
export function useGeoWorldAdmin2(enabled: boolean) {
  return useQuery({
    queryKey: ["geo", "world", "admin2"],
    queryFn: () => api.geo.worldAdmin2(),
    enabled,
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

/**
 * One world admin-1 region's detail (name, country, baked Wikidata
 * metrics, bbox, admin-2 child count). Cached server-side; client cache
 * is permanent within a session.
 */
export function useWorldAdmin1Detail(code: string | undefined) {
  return useQuery({
    queryKey: ["geo", "world", "admin1", code],
    queryFn: () => api.geo.worldAdmin1Detail(code as string),
    enabled: !!code,
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

/**
 * Substring search across world admin-1 region names. Hit when the user
 * types in the global QuickSearch bar; the backend hits a 30-min Redis
 * cache for repeated queries.
 */
export function useWorldSearch(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: ["geo", "world", "search", trimmed],
    queryFn: () => api.geo.worldSearch(trimmed),
    enabled: trimmed.length >= 2,
    staleTime: 60_000,
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
  // Memoise on the underlying query data/updatedAt so the merged
  // FeatureCollection keeps a stable identity across renders that don't change
  // the data. Without this, useGeoJsonSource's effect would re-run setData()
  // and re-parse thousands of features on every parent render.
  const signature = queries.map((q) => q.dataUpdatedAt).join(",");
  return useMemo(() => {
    const features: GeoJSON.Feature[] = [];
    for (const q of queries) {
      if (q.data?.features) features.push(...q.data.features);
    }
    if (features.length === 0) return null;
    return { type: "FeatureCollection", features };
    // queries is read inside but `signature` captures the data identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature]);
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
 * Per-metric min/max across every commune, computed once by
 * {@code CityTileBuilder} during the tile rebuild. Used to size the
 * choropleth legend when the vector-tile path is on so colouring doesn't
 * wait on the slow {@code /stats/cities} request. Cached aggressively
 * since the value only shifts after a DVF import.
 */
export function useTileMetricRanges() {
  return useQuery({
    queryKey: ["tiles", "metric-ranges"],
    queryFn: () => api.tiles.metricRanges(),
    staleTime: 30 * 60_000,
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
/**
 * Top-N nearest comparable sales for a given transaction. The endpoint is
 * stable (returns []) before the Spark job has populated the pre-agg, so
 * the hook can be wired in the popup unconditionally and simply renders
 * "no comparables yet" when the array is empty.
 */
export function useComparableSales(transactionId: number | null) {
  return useQuery({
    queryKey: ["comparableSales", transactionId],
    queryFn: () => {
      if (transactionId == null) {
        return Promise.reject(new Error("transactionId is required"));
      }
      return api.transactions.comparableSales(transactionId);
    },
    enabled: transactionId != null,
    staleTime: 30 * 60_000,
  });
}

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
  // See useGeoCitiesForDepartments: memoise so the merged collection keeps a
  // stable identity and the MapLibre source isn't re-fed on every render.
  const signature = queries.map((q) => q.dataUpdatedAt).join(",");
  return useMemo(() => {
    const features: GeoJSON.Feature[] = [];
    for (const q of queries) {
      if (q.data?.features) features.push(...q.data.features);
    }
    if (features.length === 0) return null;
    return { type: "FeatureCollection", features };
    // queries is read inside but `signature` captures the data identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature]);
}
