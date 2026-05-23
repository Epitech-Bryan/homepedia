/**
 * Tiny direct-to-Overpass client for POIs. Overpass-API exposes permissive
 * CORS so we hit it from the browser with no backend proxy, then cache
 * the result via TanStack Query. Bbox is rounded to 2 decimals (~1 km
 * cells) so a small pan reuses the same cache entry rather than
 * generating a fresh query on every move.
 *
 * <p>
 * Rate limit: Overpass throttles aggressive clients. We cap the result
 * to ~150 nodes per query (in-bbox) which keeps payloads under ~20 KB
 * and request time under 1 s on typical zooms.
 */

export type OsmPoiType = "museum" | "station" | "school" | "hospital" | "park" | "attraction";

export interface OsmPoi {
  id: number;
  type: OsmPoiType;
  name: string;
  lat: number;
  lon: number;
}

const OVERPASS_URL = "https://overpass-api.de/api/interpreter";

/**
 * Round a bbox to 2 decimal places. Two viewports within ~1 km of each
 * other will hit the same cache entry, which matches Overpass's
 * geographic resolution well enough for browse-level POIs.
 */
export function roundBbox(b: { south: number; west: number; north: number; east: number }) {
  const r = (n: number) => Math.round(n * 100) / 100;
  return { south: r(b.south), west: r(b.west), north: r(b.north), east: r(b.east) };
}

const TYPE_BY_TAG: Array<[OsmPoiType, string]> = [
  ["museum", 'node["tourism"="museum"]["name"]'],
  ["station", 'node["railway"="station"]["name"]'],
  ["school", 'node["amenity"="school"]["name"]'],
  ["hospital", 'node["amenity"="hospital"]["name"]'],
  ["park", 'node["leisure"="park"]["name"]'],
  ["attraction", 'node["tourism"="attraction"]["name"]'],
];

function buildQuery(bbox: { south: number; west: number; north: number; east: number }): string {
  const b = `${bbox.south},${bbox.west},${bbox.north},${bbox.east}`;
  const parts = TYPE_BY_TAG.map(([, sel]) => `${sel}(${b});`).join("\n");
  // out:json + 10s timeout — Overpass server-side; keeps slow clusters
  // from holding our worker. tag=name guarantees the row is map-worthy.
  return `[out:json][timeout:10];(\n${parts}\n);out body 150;`;
}

function classify(tags: Record<string, string>): OsmPoiType | null {
  if (tags.tourism === "museum") return "museum";
  if (tags.railway === "station") return "station";
  if (tags.amenity === "school") return "school";
  if (tags.amenity === "hospital") return "hospital";
  if (tags.leisure === "park") return "park";
  if (tags.tourism === "attraction") return "attraction";
  return null;
}

export async function fetchOsmPois(bbox: {
  south: number;
  west: number;
  north: number;
  east: number;
}): Promise<OsmPoi[]> {
  const body = new URLSearchParams({ data: buildQuery(bbox) });
  const res = await fetch(OVERPASS_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!res.ok) {
    throw new Error(`Overpass returned ${res.status}`);
  }
  const json = (await res.json()) as {
    elements: Array<{ id: number; lat: number; lon: number; tags?: Record<string, string> }>;
  };
  const out: OsmPoi[] = [];
  for (const el of json.elements ?? []) {
    const tags = el.tags ?? {};
    const type = classify(tags);
    const name = tags.name;
    if (!type || !name) continue;
    out.push({ id: el.id, type, name, lat: el.lat, lon: el.lon });
  }
  return out;
}
