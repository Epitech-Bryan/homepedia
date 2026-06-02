import { useEffect, type RefObject } from "react";
import maplibregl from "maplibre-gl";
import type { OsmPoi } from "@/api/osm";

export type HeatPoint = { latitude: number; longitude: number; value: number };

export type TransactionMarker = {
  id: number;
  latitude: number;
  longitude: number;
  propertyValue: number;
  propertyType: string;
  builtSurface: number | null;
  roomCount: number | null;
  mutationDate: string;
};

export function emptyFeatureCollection(): GeoJSON.FeatureCollection {
  return { type: "FeatureCollection", features: [] };
}

export function buildHeatFeatures(points: HeatPoint[]): GeoJSON.FeatureCollection {
  const maxV = points.reduce((m, p) => Math.max(m, p.value), 0) || 1;
  return {
    type: "FeatureCollection",
    features: points.map((p) => ({
      type: "Feature",
      geometry: { type: "Point", coordinates: [p.longitude, p.latitude] },
      properties: { w: p.value / maxV },
    })),
  };
}

export function buildTransactionFeatures(rows: TransactionMarker[]): GeoJSON.FeatureCollection {
  return {
    type: "FeatureCollection",
    features: rows.map((t) => ({
      type: "Feature",
      geometry: { type: "Point", coordinates: [t.longitude, t.latitude] },
      properties: {
        value: t.propertyValue,
        ppsqm: t.builtSurface && t.builtSurface > 0 ? t.propertyValue / t.builtSurface : 0,
        type: t.propertyType,
        surface: t.builtSurface,
        rooms: t.roomCount,
        date: t.mutationDate,
      },
    })),
  };
}

export function buildPoiFeatures(pois: OsmPoi[]): GeoJSON.FeatureCollection {
  return {
    type: "FeatureCollection",
    features: pois.map((p) => ({
      type: "Feature",
      geometry: { type: "Point", coordinates: [p.lon, p.lat] },
      properties: { name: p.name, type: p.type },
    })),
  };
}

// Pushes a FeatureCollection into an already-declared GeoJSON source once the
// style is ready. Replaces the three near-identical heat/txns/poi update
// effects that each re-implemented the getSource + isStyleLoaded/idle dance.
export function useGeoJsonSource(
  mapRef: RefObject<maplibregl.Map | null>,
  sourceId: string,
  data: GeoJSON.FeatureCollection,
): void {
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const apply = () => {
      const src = map.getSource(sourceId) as maplibregl.GeoJSONSource | undefined;
      if (src) src.setData(data);
    };
    if (map.isStyleLoaded()) apply();
    else map.once("idle", apply);
  }, [mapRef, sourceId, data]);
}
