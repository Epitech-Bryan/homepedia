/// <reference types="vite/client" />

// Project-specific Vite env vars surfaced through import.meta.env. Keeps
// the type checker happy and lists every flag the app reads so it's easy
// to scan from one file.
interface ImportMetaEnv {
  /**
   * When set to "true", the map renders commune polygons at city zoom
   * through the backend vector-tile endpoint (issue #6) instead of the
   * GeoJSON fetched from geo.api.gouv.fr.
   */
  readonly VITE_USE_VECTOR_TILES?: string;
  /**
   * When set to "true", the world layers (countries, admin-1, admin-2)
   * are rendered through the {@code /api/tiles/world} MVT endpoint
   * instead of the SVG GeoJSON path. Independent of VITE_USE_VECTOR_TILES.
   */
  readonly VITE_USE_WORLD_TILES?: string;
  /** Backend origin used in dev — Vite proxies /api to this target. */
  readonly VITE_API_TARGET?: string;
  /**
   * When not "false" (the default), the main map renders through MapLibre
   * GL with a globe projection instead of the flat Leaflet map. Set to
   * "false" to fall back to the Leaflet renderer.
   */
  readonly VITE_USE_MAPLIBRE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
