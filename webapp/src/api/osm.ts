/**
 * Frontend client for OSM POIs. We hit our own backend
 * ({@code /api/geo/pois}) which proxies Overpass with a 7-day Redis
 * cache, so pans within a previously-visited region pay zero network
 * cost and Overpass itself is hit at most once per (bbox, week) instead
 * of once per user per pan.
 *
 * <p>
 * Bbox is rounded to 2 decimals (~1 km cells) before being sent so the
 * backend cache key is stable for small pans.
 */

export type OsmPoiType = "museum" | "station" | "school" | "hospital" | "park" | "attraction";

export interface OsmPoi {
  id: number;
  type: OsmPoiType;
  name: string;
  lat: number;
  lon: number;
}

export function roundBbox(b: { south: number; west: number; north: number; east: number }) {
  const r = (n: number) => Math.round(n * 100) / 100;
  return { south: r(b.south), west: r(b.west), north: r(b.north), east: r(b.east) };
}

export async function fetchOsmPois(bbox: {
  south: number;
  west: number;
  north: number;
  east: number;
}): Promise<OsmPoi[]> {
  const qs = new URLSearchParams({
    south: String(bbox.south),
    west: String(bbox.west),
    north: String(bbox.north),
    east: String(bbox.east),
  });
  const res = await fetch(`/api/geo/pois?${qs.toString()}`, { credentials: "same-origin" });
  if (!res.ok) {
    throw new Error(`POIs endpoint returned ${res.status}`);
  }
  const json = (await res.json()) as Array<{
    id: number;
    type: OsmPoiType;
    name: string;
    lat: number;
    lon: number;
  }>;
  return json;
}
