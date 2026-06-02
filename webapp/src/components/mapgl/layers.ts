import maplibregl from "maplibre-gl";
import type { OsmPoiType } from "@/api/osm";
import type { MapStyle } from "../FranceMap";
import { type MapMetricKey, HOVER_LINE, LAYER_DEFS, recomputeBubbles } from "./choropleth";
import { emptyFeatureCollection } from "./sources";

export const POI_MIN_ZOOM = 12;

export const POI_COLOR: Record<OsmPoiType, string> = {
  museum: "#a855f7",
  station: "#0ea5e9",
  school: "#f59e0b",
  hospital: "#ef4444",
  park: "#10b981",
  attraction: "#ec4899",
};

const hoverCase = (whenHover: unknown, otherwise: unknown): maplibregl.ExpressionSpecification =>
  [
    "case",
    ["boolean", ["feature-state", "hover"], false],
    whenHover,
    otherwise,
  ] as maplibregl.ExpressionSpecification;

// Adds the choropleth fill/outline layers plus the heat / POI / transaction /
// bubble overlays once, guarded so a re-run never duplicates them. The initial
// fill colour is passed in so the choropleth paints from a prop-derived range
// the instant tiles arrive, rather than flashing grey until the first recolor.
export function installLayers(
  map: maplibregl.Map,
  initialColor: maplibregl.ExpressionSpecification,
): void {
  for (const d of LAYER_DEFS) {
    if (map.getLayer(d.id)) continue;
    map.addLayer({
      id: d.id,
      type: "fill",
      source: d.source,
      "source-layer": d.sourceLayer,
      minzoom: d.minzoom,
      maxzoom: d.maxzoom,
      paint: {
        "fill-color": initialColor,
        "fill-opacity": hoverCase(0.9, 0.62),
      },
    });
    map.addLayer({
      id: `${d.id}-line`,
      type: "line",
      source: d.source,
      "source-layer": d.sourceLayer,
      minzoom: d.minzoom,
      maxzoom: d.maxzoom,
      paint: {
        "line-color": hoverCase(HOVER_LINE, "#7c2d12"),
        "line-width": hoverCase(2, 0.5),
        "line-opacity": 0.7,
      },
    });
  }

  if (!map.getSource("heat")) {
    map.addSource("heat", { type: "geojson", data: emptyFeatureCollection() });
    map.addLayer({
      id: "heat-layer",
      type: "heatmap",
      source: "heat",
      layout: { visibility: "none" },
      paint: {
        "heatmap-weight": ["interpolate", ["linear"], ["get", "w"], 0, 0, 1, 1],
        "heatmap-intensity": ["interpolate", ["linear"], ["zoom"], 0, 0.6, 9, 1, 16, 2.4],
        "heatmap-radius": ["interpolate", ["linear"], ["zoom"], 6, 8, 11, 16, 16, 32],
        "heatmap-opacity": 0.78,
        "heatmap-color": [
          "interpolate",
          ["linear"],
          ["heatmap-density"],
          0,
          "rgba(69,117,180,0)",
          0.2,
          "#74add1",
          0.4,
          "#fee090",
          0.6,
          "#fdae61",
          0.8,
          "#f46d43",
          1,
          "#d73027",
        ],
      },
    });
  }
  if (!map.getSource("poi")) {
    map.addSource("poi", { type: "geojson", data: emptyFeatureCollection() });
    map.addLayer({
      id: "poi-layer",
      type: "circle",
      source: "poi",
      minzoom: POI_MIN_ZOOM,
      paint: {
        "circle-radius": 5,
        "circle-stroke-width": 1.5,
        "circle-stroke-color": "#ffffff",
        "circle-color": [
          "match",
          ["get", "type"],
          "museum",
          POI_COLOR.museum,
          "station",
          POI_COLOR.station,
          "school",
          POI_COLOR.school,
          "hospital",
          POI_COLOR.hospital,
          "park",
          POI_COLOR.park,
          "attraction",
          POI_COLOR.attraction,
          "#888888",
        ],
        "circle-opacity": 0.85,
      },
    });
  }
  if (!map.getSource("txns")) {
    map.addSource("txns", { type: "geojson", data: emptyFeatureCollection() });
    map.addLayer({
      id: "txns-layer",
      type: "circle",
      source: "txns",
      paint: {
        "circle-radius": ["interpolate", ["linear"], ["zoom"], 11, 3, 16, 7],
        "circle-stroke-width": 1,
        "circle-stroke-color": "#ffffff",
        "circle-color": [
          "interpolate",
          ["linear"],
          ["get", "ppsqm"],
          1000,
          "#1a9850",
          3000,
          "#fee08b",
          6000,
          "#f46d43",
          12000,
          "#a50026",
        ],
      },
    });
  }
  if (!map.getSource("bubbles")) {
    map.addSource("bubbles", { type: "geojson", data: emptyFeatureCollection() });
    map.addLayer({
      id: "bubbles-layer",
      type: "circle",
      source: "bubbles",
      layout: { visibility: "none" },
      paint: {
        "circle-radius": ["get", "r"],
        "circle-color": "#fc8d59",
        "circle-opacity": 0.6,
        "circle-stroke-color": "#b3502c",
        "circle-stroke-width": 1.5,
      },
    });
  }
}

// Toggles which overlay is visible for the selected map style: choropleth fills
// + outlines, the heatmap, or the bubble layer (recomputed on demand since its
// centroids depend on whatever fills are currently rendered).
export function applyLayerVisibility(
  map: maplibregl.Map,
  mapStyle: MapStyle,
  metricKey: MapMetricKey,
): void {
  const showFills = mapStyle !== "heat" && mapStyle !== "bubbles";
  const showHeat = mapStyle === "heat" || mapStyle === "all";
  const showBubbles = mapStyle === "bubbles";
  for (const id of LAYER_DEFS.flatMap((d) => [d.id, `${d.id}-line`])) {
    if (map.getLayer(id)) {
      map.setLayoutProperty(id, "visibility", showFills ? "visible" : "none");
    }
  }
  if (map.getLayer("heat-layer")) {
    map.setLayoutProperty("heat-layer", "visibility", showHeat ? "visible" : "none");
  }
  if (map.getLayer("bubbles-layer")) {
    map.setLayoutProperty("bubbles-layer", "visibility", showBubbles ? "visible" : "none");
  }
  if (showBubbles) recomputeBubbles(map, metricKey);
}
