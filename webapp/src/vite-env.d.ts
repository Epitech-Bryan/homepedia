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
  /** Backend origin used in dev — Vite proxies /api to this target. */
  readonly VITE_API_TARGET?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
