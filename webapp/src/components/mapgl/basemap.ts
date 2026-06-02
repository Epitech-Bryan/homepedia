import maplibregl from "maplibre-gl";

export type Basemap = "voyager" | "satellite";

export const RASTER_TILES: Record<Basemap, string[]> = {
  voyager: [
    "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png",
    "https://b.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png",
    "https://c.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png",
    "https://d.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png",
  ],
  satellite: [
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
  ],
};

// Builds the globe map shell: raster basemap + the world/cities vector tile
// sources, globe projection, navigation control, rotation disabled, and the
// atmospheric sky. Data layers are added separately (see installLayers); this
// only stands up the canvas the rest of the component wires handlers onto.
export function createGlobeMap(container: HTMLElement, basemap: Basemap): maplibregl.Map {
  const map = new maplibregl.Map({
    container,
    center: [10, 20],
    zoom: 2,
    minZoom: 0.5,
    maxZoom: 19,
    attributionControl: { compact: true },
    style: {
      version: 8,
      projection: { type: "globe" },
      glyphs: "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
      sources: {
        basemap: {
          type: "raster",
          tiles: RASTER_TILES[basemap],
          tileSize: 256,
          attribution: "&copy; OSM &copy; CARTO / Esri",
        },
        world: {
          type: "vector",
          tiles: [`${location.origin}/api/tiles/world/{z}/{x}/{y}.pbf`],
          minzoom: 0,
          maxzoom: 12,
          promoteId: "code",
        },
        cities: {
          type: "vector",
          tiles: [`${location.origin}/api/tiles/cities/{z}/{x}/{y}.pbf`],
          minzoom: 4,
          maxzoom: 14,
          promoteId: "code",
        },
      },
      layers: [{ id: "basemap", type: "raster", source: "basemap" }],
    },
  });

  map.addControl(
    new maplibregl.NavigationControl({ showCompass: true, visualizePitch: true }),
    "bottom-right",
  );
  map.dragRotate.disable();
  map.touchZoomRotate.disableRotation();

  map.on("style.load", () => {
    map.setSky({
      "sky-color": "#9cc6ff",
      "sky-horizon-blend": 0.5,
      "horizon-color": "#ffffff",
      "horizon-fog-blend": 0.5,
      "fog-color": "#dfe9f5",
      "fog-ground-blend": 0.6,
    });
  });

  return map;
}
