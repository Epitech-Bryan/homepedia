import { useEffect, useRef } from "react";
import { useMap } from "react-leaflet";
// `leaflet-vectorgrid-setup` publishes Leaflet on `globalThis.L` before the
// plugin loads, so the side-effect `L.vectorGrid = …` lands on the same
// instance MapContainer uses. Importing directly from `"leaflet.vectorgrid"`
// or `"leaflet"` here breaks the plugin under Vite's ESM module graph —
// the plugin attaches to a different L and tile fetches never fire.
// Types declared inline in src/types/leaflet-vectorgrid.d.ts.
import L from "@/lib/leaflet-vectorgrid-setup";

interface CityVectorGridLayerProps {
  /**
   * URL template the layer hits per visible tile. Defaults to the rest-api
   * endpoint shipped in issue #6 step 1; can be overridden in dev/preview.
   */
  url?: string;
  /**
   * Map of (commune code → metric value) used to colour each polygon.
   * The same shape `FranceMap` uses for its choropleth, so the parent can
   * pass `metricByCode` straight through.
   */
  metricByCode?: Record<string, number | null | undefined>;
  /**
   * Min/max of the visible metric — required for the colour ramp.
   */
  range?: { min: number; max: number } | null;
  /**
   * Sequential palette to map ratio → fill. Caller's choice so the colour
   * scheme stays consistent with the rest of the map.
   */
  palette: string[];
  /**
   * Fired when the user clicks a polygon. Receives the commune code from
   * the tile's `code` feature property.
   */
  onFeatureClick?: (code: string, name?: string) => void;
}

/**
 * Renders the commune polygons by streaming MVT tiles from the backend.
 * Replaces the GeoJSON-per-department fetch that today happens through
 * geo.api.gouv.fr — the tiles ship only what's visible, decode on the
 * canvas worker thread, and avoid putting thousands of SVG nodes in the
 * DOM (Leaflet's GeoJSON layer's bottleneck past zoom 11 in Paris/Lyon).
 *
 * <p>
 * Mounted behind a feature flag in {@code PersistentMap}; toggling the
 * flag back to GeoJSON is a one-line revert if the tile bundle is stale
 * or the endpoint returns 404s.
 */
export function CityVectorGridLayer({
  url = "/api/tiles/cities/{z}/{x}/{y}.pbf",
  metricByCode,
  range,
  palette,
  onFeatureClick,
}: CityVectorGridLayerProps) {
  const map = useMap();

  // Refs let the style closure read the LATEST props without forcing the
  // layer to remount. Without this, every pan at city zoom recomputes
  // `metricByCode` (new viewport → new visible departments → new commune
  // stats), the effect tears down VectorGrid, and pending MVT tile fetches
  // resolve against a dead canvas pane — crashing the whole MapContainer
  // with `Cannot read properties of undefined (reading '_leaflet_pos')`.
  const metricByCodeRef = useRef(metricByCode);
  const rangeRef = useRef(range);
  const paletteRef = useRef(palette);
  const onFeatureClickRef = useRef(onFeatureClick);
  useEffect(() => {
    metricByCodeRef.current = metricByCode;
    rangeRef.current = range;
    paletteRef.current = palette;
    onFeatureClickRef.current = onFeatureClick;
  });

  // Mount the layer once per (map, url) pair. Style updates flow through
  // a redraw triggered by the second effect below.
  const layerRef = useRef<(L.Layer & { redraw?: () => void }) | null>(null);
  useEffect(() => {
    const styleFor = (props: { code?: string }) => {
      const code = props.code;
      const m = metricByCodeRef.current;
      const r = rangeRef.current;
      const p = paletteRef.current;
      const value = code != null ? m?.[code] : null;
      if (value == null || !r || !Number.isFinite(value) || value <= 0) {
        return {
          fillColor: "#e5e7eb",
          fill: true,
          fillOpacity: 0.5,
          weight: 0.4,
          color: "#9ca3af",
        };
      }
      const ratio = (value - r.min) / Math.max(r.max - r.min, 1);
      const idx = Math.min(p.length - 1, Math.max(0, Math.floor(ratio * p.length)));
      return {
        fillColor: p[idx],
        fill: true,
        fillOpacity: 0.7,
        weight: 0.4,
        color: "#374151",
      };
    };

    // `L.vectorGrid` exists at runtime via the side-effect import above.
    const layer = (
      L as unknown as {
        vectorGrid: {
          protobuf: (
            url: string,
            options: unknown,
          ) => L.Layer & { on: (ev: string, cb: unknown) => void; redraw?: () => void };
        };
      }
    ).vectorGrid.protobuf(url, {
      // Tippecanoe writes the source layer under the name we pass to
      // `--layer` in the generation script. Keep this in sync with
      // docs/vector-tiles.md.
      vectorTileLayerStyles: {
        cities: (props: { code?: string }) => styleFor(props),
      },
      interactive: true,
      // The mbtiles ship z=0..14 (TileController guard line, kept in sync
      // with the Tippecanoe `-zg --maximum-zoom=14` pipeline). Without
      // maxNativeZoom, VectorGrid would request z=15+ tiles, hit a 404,
      // and the whole layer would vanish past street-level zoom. Capping
      // here makes the z=14 tile rasterise up to the basemap's z=20 — a
      // bit pixellated past z=18 but commune polygons stay visible.
      maxNativeZoom: 14,
      // Allow the canvas to extend slightly past the tile edge so polygon
      // borders don't show pixel gaps at tile boundaries.
      getFeatureId: (f: { properties?: { code?: string } }) => f.properties?.code,
    });

    layer.on("click", (e: { layer: { properties?: { code?: string; name?: string } } }) => {
      const code = e.layer?.properties?.code;
      if (code) {
        onFeatureClickRef.current?.(code, e.layer.properties?.name);
      }
    });

    layer.addTo(map);
    layerRef.current = layer;
    return () => {
      layerRef.current = null;
      // Tile fetches in flight may resolve after remove(); swallow the
      // resulting null-pane crash so React keeps the MapContainer alive.
      try {
        layer.remove();
      } catch {
        // ignore
      }
    };
  }, [map, url]);

  // Re-render polygons when the choropleth inputs change. Reads the current
  // values via refs and asks VectorGrid to repaint — no remount needed.
  useEffect(() => {
    const layer = layerRef.current;
    if (layer && typeof layer.redraw === "function") {
      layer.redraw();
    }
  }, [metricByCode, range, palette]);

  return null;
}
