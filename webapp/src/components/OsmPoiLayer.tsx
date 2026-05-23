import { useEffect, useMemo } from "react";
import { useMap } from "react-leaflet";
import { useQuery } from "@tanstack/react-query";
import L from "leaflet";
import { fetchOsmPois, roundBbox, type OsmPoiType } from "@/api/osm";

interface OsmPoiLayerProps {
  bounds: { south: number; west: number; north: number; east: number };
  minZoom: number;
  zoom: number;
}

const ICON_BY_TYPE: Record<OsmPoiType, { color: string; label: string }> = {
  museum: { color: "#a855f7", label: "Musée" },
  station: { color: "#0ea5e9", label: "Gare" },
  school: { color: "#f59e0b", label: "École" },
  hospital: { color: "#ef4444", label: "Hôpital" },
  park: { color: "#10b981", label: "Parc" },
  attraction: { color: "#ec4899", label: "Attraction" },
};

/**
 * Renders OSM POIs (museums / stations / schools / hospitals / parks /
 * attractions) as small coloured circle markers above the basemap.
 * Only mounts past {@code minZoom} (default z=12 from the caller) so
 * Overpass isn't hammered at low zoom where the bbox would return
 * tens of thousands of nodes — and the markers would be unreadable
 * anyway at that scale.
 *
 * <p>
 * Bbox is rounded by {@link roundBbox} before the query, so panning
 * within a ~1 km window reuses the same TanStack Query cache entry.
 * staleTime is set to Infinity since OSM POIs don't move and adding
 * a refresh button would be overkill for browse-only data.
 */
export function OsmPoiLayer({ bounds, minZoom, zoom }: OsmPoiLayerProps) {
  const map = useMap();
  const rounded = useMemo(() => roundBbox(bounds), [bounds]);
  const enabled = zoom >= minZoom;
  const { data } = useQuery({
    queryKey: ["osm", "pois", rounded],
    queryFn: () => fetchOsmPois(rounded),
    enabled,
    staleTime: Infinity,
    gcTime: 30 * 60_000,
  });

  useEffect(() => {
    if (!enabled || !data || data.length === 0) {
      return;
    }
    const group = L.layerGroup();
    for (const poi of data) {
      const style = ICON_BY_TYPE[poi.type];
      const marker = L.circleMarker([poi.lat, poi.lon], {
        radius: 5,
        color: "#ffffff",
        weight: 1.5,
        fillColor: style.color,
        fillOpacity: 0.85,
      });
      marker.bindTooltip(
        `<strong>${escapeHtml(poi.name)}</strong><br/><span style="color:#6b7280">${style.label}</span>`,
        {
          direction: "top",
          offset: [0, -6],
        },
      );
      marker.addTo(group);
    }
    group.addTo(map);
    return () => {
      group.remove();
    };
  }, [data, enabled, map]);

  return null;
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => {
    switch (c) {
      case "&":
        return "&amp;";
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case '"':
        return "&quot;";
      default:
        return "&#39;";
    }
  });
}
