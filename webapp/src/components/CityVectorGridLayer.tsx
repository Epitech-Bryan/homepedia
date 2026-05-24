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
   * Reads the choropleth value out of a feature's MVT properties. Used when
   * stats are baked into the tile by `CityTileBuilder` so the layer can
   * colour each polygon without an out-of-band `/stats/cities` request.
   * Returning {@code null}/{@code undefined} falls through to
   * {@link CityVectorGridLayerProps.metricByCode}, which keeps the layer
   * working on legacy tiles that ship `code` only.
   */
  metricFromFeature?: (props: Record<string, unknown>) => number | null | undefined;
  /**
   * Map of (commune code → metric value) used to colour each polygon when
   * {@link CityVectorGridLayerProps.metricFromFeature} returns nothing.
   * The same shape `FranceMap` uses for its choropleth, so the parent can
   * pass `metricByCode` straight through.
   */
  metricByCode?: Record<string, number | null | undefined>;
  /**
   * Min/max of the visible metric plus optional quantile break-points
   * (6 internal cuts → 7 buckets matching the palette). When {@code breaks}
   * is provided the layer bins each feature against the cuts rather than
   * computing a linear ratio, so a single outlier (Paris at 2 M pop) can't
   * collapse every other commune onto the lightest colour.
   */
  range?: { min: number; max: number; breaks?: number[] } | null;
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
  /**
   * Optional label appended to the hover tooltip value (e.g. "Population",
   * "Avg. €/m²"). Kept in parity with the LeafletGeoJSON path so users
   * see the same metric context whether vector tiles are on or off.
   */
  metricLabel?: string;
}

function formatValue(value: number): string {
  if (value >= 1_000_000) return (value / 1_000_000).toFixed(1) + "M";
  if (value >= 1_000) return (value / 1_000).toFixed(1) + "k";
  return Math.round(value).toLocaleString("fr-FR");
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
// Dedicated pane name so the city VG canvas tiles always render above the
// world VG canvas tiles (`worldVectorTiles`, zIndex 410). Without explicit
// panes both layers land in the default `tilePane` and Leaflet has no
// deterministic z-order between them, so the world admin-2 canvas (still
// active at z=9-10 over France with a transparent-but-interactive fill)
// can absorb mouseover/click before the city layer sees them — exactly
// what was breaking hover/click on Lille at city zoom.
const CITY_VG_PANE = "cityVectorTiles";

export function CityVectorGridLayer({
  url = "/api/tiles/cities/{z}/{x}/{y}.pbf",
  metricFromFeature,
  metricByCode,
  range,
  palette,
  onFeatureClick,
  metricLabel,
}: CityVectorGridLayerProps) {
  const map = useMap();

  if (!map.getPane(CITY_VG_PANE)) {
    const pane = map.createPane(CITY_VG_PANE);
    pane.style.zIndex = "420";
    pane.style.pointerEvents = "auto";
  }

  // Refs let the style closure read the LATEST props without forcing the
  // layer to remount. Without this, every pan at city zoom recomputes
  // `metricByCode` (new viewport → new visible departments → new commune
  // stats), the effect tears down VectorGrid, and pending MVT tile fetches
  // resolve against a dead canvas pane — crashing the whole MapContainer
  // with `Cannot read properties of undefined (reading '_leaflet_pos')`.
  const metricFromFeatureRef = useRef(metricFromFeature);
  const metricByCodeRef = useRef(metricByCode);
  const rangeRef = useRef(range);
  const paletteRef = useRef(palette);
  const onFeatureClickRef = useRef(onFeatureClick);
  const metricLabelRef = useRef(metricLabel);
  useEffect(() => {
    metricFromFeatureRef.current = metricFromFeature;
    metricByCodeRef.current = metricByCode;
    rangeRef.current = range;
    paletteRef.current = palette;
    onFeatureClickRef.current = onFeatureClick;
    metricLabelRef.current = metricLabel;
  });

  // Mount the layer once per (map, url) pair. Style updates flow through
  // a redraw triggered by the second effect below.
  const layerRef = useRef<(L.Layer & { redraw?: () => void }) | null>(null);
  useEffect(() => {
    const styleFor = (props: Record<string, unknown> & { code?: string }) => {
      const code = props.code;
      const m = metricByCodeRef.current;
      const r = rangeRef.current;
      const p = paletteRef.current;
      // Prefer tile-embedded stats when available (CityTileBuilder bakes
      // them into the MVT properties). Fall back to the parent-supplied
      // map so legacy tiles without baked stats still render — useful
      // during the rolling deploy where the new mbtiles hasn't shipped
      // to the PVC yet.
      const fromFeature = metricFromFeatureRef.current?.(props);
      const value =
        fromFeature != null && Number.isFinite(fromFeature)
          ? fromFeature
          : code != null
            ? m?.[code]
            : null;
      // `interactive: true` on every feature is what actually wires hit
      // testing in leaflet-vectorgrid — the top-level `interactive` option
      // gates the layer's event dispatch but the per-feature flag is what
      // makes the canvas tile renderer record the polygon path for hit
      // checks. Without it, click/mouseover never fire and the layer just
      // shows up as a static fill the user can pan over but not interact
      // with.
      if (value == null || !r || !Number.isFinite(value) || value <= 0) {
        return {
          fillColor: "#e5e7eb",
          fill: true,
          fillOpacity: 0.5,
          weight: 0.4,
          color: "#9ca3af",
          interactive: true,
        };
      }
      let idx: number;
      const breaks = r.breaks;
      if (breaks && breaks.length > 0) {
        // Quantile binning: each break-point separates 1/7 of the communes
        // from the next. Iterate until we land in our bucket — the array is
        // short enough (6 cuts) that a linear scan beats Array#findIndex.
        idx = breaks.length;
        for (let i = 0; i < breaks.length; i++) {
          if (value <= breaks[i]) {
            idx = i;
            break;
          }
        }
        idx = Math.min(p.length - 1, idx);
      } else {
        // Fallback: linear ratio over [min..max] when the backend hasn't
        // shipped breakpoints yet (older API or empty distribution).
        const ratio = (value - r.min) / Math.max(r.max - r.min, 1);
        idx = Math.min(p.length - 1, Math.max(0, Math.floor(ratio * p.length)));
      }
      return {
        fillColor: p[idx],
        fill: true,
        fillOpacity: 0.7,
        weight: 0.4,
        color: "#374151",
        interactive: true,
      };
    };

    // `L.vectorGrid` exists at runtime via the side-effect import above.
    // VectorGrid features live inside a Canvas, so they don't carry
    // `bindTooltip`/`bindPopup` like real Leaflet layers — we have to drive
    // a single floating tooltip + per-feature style swap manually through
    // the layer's mouse events.
    type VGLayer = L.Layer & {
      on: (ev: string, cb: unknown) => VGLayer;
      redraw?: () => void;
      setFeatureStyle: (id: string, style: L.PathOptions) => void;
      resetFeatureStyle: (id: string) => void;
    };
    const layer = (
      L as unknown as {
        vectorGrid: {
          protobuf: (url: string, options: unknown) => VGLayer;
        };
      }
    ).vectorGrid.protobuf(url, {
      // Render into the dedicated pane created above so this layer
      // always sits on top of `worldVectorTiles` (zIndex 410) and
      // claims pointer events first over France/Belgium.
      pane: CITY_VG_PANE,
      // The mbtiles ships four layers: regions (z 4-6), departments
      // (z 7-8), cities (z 9-14), iris (z 13-14). Each level shares the
      // same styleFor closure — the `code` property is the join key
      // across the first three. The iris layer carries a 9-char code
      // (no commune-level metric) so it falls back to a neutral fill
      // through styleFor's null branch.
      vectorTileLayerStyles: {
        // Each style fn receives (props, zoom). Tippecanoe's
        // --extend-zooms-if-still-dropping leaks `regions` (built for z=4-6)
        // and `departments` (built for z=7-8) into z>=9 tiles when the
        // densest features get dropped at maxzoom. Drawing them at city
        // zoom paints the FR départements as one big fill *on top* of the
        // commune polygons. Gate each level to its intended zoom band so
        // only one administrative layer is visible at a time:
        //   z=4-6 → regions, z=7-8 → departments, z>=9 → cities.
        regions: (props: Record<string, unknown> & { code?: string }, zoom: number) =>
          zoom <= 6
            ? styleFor(props)
            : { fill: false, stroke: false, weight: 0, interactive: false },
        departments: (props: Record<string, unknown> & { code?: string }, zoom: number) =>
          zoom >= 7 && zoom <= 8
            ? styleFor(props)
            : { fill: false, stroke: false, weight: 0, interactive: false },
        cities: (props: Record<string, unknown> & { code?: string }, zoom: number) =>
          zoom >= 9
            ? styleFor(props)
            : { fill: false, stroke: false, weight: 0, interactive: false },
        // IRIS gets its own style: thin border + soft translucent fill so
        // the underlying commune choropleth bleeds through. No hover
        // colour change either — just a tooltip with the IRIS name and
        // a click that routes to the parent commune.
        iris: () => ({
          fillColor: "#ffffff",
          fill: true,
          fillOpacity: 0.05,
          weight: 0.6,
          color: "#1f2937",
          interactive: true,
        }),
      },
      interactive: true,
      // The mbtiles span z=4..14 across the three layers (TileController
      // bounds checks). Cap with min/maxNativeZoom so Leaflet upsamples
      // z=14 tiles past z=18 instead of 404-storming the console — same
      // reason as before, just with the extended min bound.
      minNativeZoom: 4,
      maxNativeZoom: 14,
      // Stable feature id across all three layers — the choropleth
      // highlight + tooltip both key off `code`.
      getFeatureId: (f: { properties?: { code?: string } }) => f.properties?.code,
    });

    layer.on(
      "click",
      (e: { layer: { properties?: { code?: string; name?: string; commune_code?: string } } }) => {
        const props = e.layer?.properties;
        if (!props) return;
        // IRIS click → route to the parent commune (commune_code is the
        // 5-char INSEE prefix baked by CityTileBuilder). The IRIS code
        // itself isn't a navigable entity in the rest of the app.
        const isIris =
          typeof props.code === "string" && props.code.length === 9 && !!props.commune_code;
        const targetCode = isIris ? props.commune_code : props.code;
        if (targetCode) {
          onFeatureClickRef.current?.(targetCode, props.name);
        }
      },
    );

    // Single floating tooltip shared across the layer — cheaper than
    // creating one per feature and avoids the canvas/DOM hit-test mismatch
    // that VectorGrid's per-feature bindTooltip workarounds suffer from.
    const tooltip = L.tooltip({
      sticky: true,
      direction: "top",
      offset: [0, -8],
      className: "leaflet-tooltip",
    });
    let hoveredId: string | null = null;

    const buildTooltipHtml = (
      code: string,
      name: string | undefined,
      props: Record<string, unknown>,
    ): string => {
      const label = name ?? code;
      // IRIS features carry a 9-char code + commune_code prop — no metric
      // baked in, so we just show the IRIS name and hint that the click
      // will navigate to the parent commune.
      const isIris = code.length === 9 && typeof props.commune_code === "string";
      if (isIris) {
        return `<strong>${label}</strong><br/><span style="color:#6b7280">IRIS · ${props.commune_code}</span>`;
      }
      // Same precedence as styleFor: tile-baked value first, parent map
      // second. Prevents a "no value" tooltip on a polygon that's already
      // coloured by the embedded stats.
      const fromFeature = metricFromFeatureRef.current?.(props);
      const fallback = metricByCodeRef.current?.[code];
      const value =
        fromFeature != null && Number.isFinite(fromFeature) ? fromFeature : (fallback ?? null);
      const ml = metricLabelRef.current;
      if (value != null && Number.isFinite(value)) {
        return `<strong>${label}</strong><br/>${formatValue(value)}${ml ? ` · ${ml}` : ""}`;
      }
      return `<strong>${label}</strong>`;
    };

    layer.on(
      "mouseover",
      (e: {
        layer: { properties?: Record<string, unknown> & { code?: string; name?: string } };
        latlng: L.LatLng;
      }) => {
        const code = e.layer?.properties?.code;
        if (!code) return;
        if (hoveredId && hoveredId !== code) {
          layer.resetFeatureStyle(hoveredId);
        }
        hoveredId = code;
        layer.setFeatureStyle(code, {
          fill: true,
          fillOpacity: 0.92,
          weight: 2.5,
          color: "#1f2937",
        });
        tooltip
          .setLatLng(e.latlng)
          .setContent(
            buildTooltipHtml(
              code,
              e.layer.properties?.name,
              (e.layer.properties ?? {}) as Record<string, unknown>,
            ),
          );
        if (!tooltip.isOpen()) tooltip.addTo(map);
      },
    );

    layer.on("mousemove", (e: { latlng: L.LatLng }) => {
      if (hoveredId) tooltip.setLatLng(e.latlng);
    });

    layer.on(
      "mouseout",
      (e: { layer?: { properties?: Record<string, unknown> & { code?: string } } }) => {
        // leaflet.vectorgrid fires mouseout per-feature. When the cursor
        // moves directly between two adjacent communes the event order
        // is sometimes mouseover(B) → mouseout(A) — resetting blindly
        // would clear B's highlight even though the cursor is still
        // over it. Only act when the leaving feature matches the
        // currently-tracked hover, OR when there's no leaving feature
        // at all (cursor left the map entirely).
        const code = e?.layer?.properties?.code;
        if (code) {
          if (hoveredId === code) {
            layer.resetFeatureStyle(code);
            hoveredId = null;
            map.closeTooltip(tooltip);
          }
        } else if (hoveredId) {
          layer.resetFeatureStyle(hoveredId);
          hoveredId = null;
          map.closeTooltip(tooltip);
        }
      },
    );

    layer.addTo(map);
    layerRef.current = layer;
    return () => {
      layerRef.current = null;
      map.closeTooltip(tooltip);
      // Tile fetches in flight may resolve after remove(); swallow the
      // resulting null-pane crash so React keeps the MapContainer alive.
      try {
        layer.remove();
      } catch {
        // ignore
      }
    };
  }, [map, url]);

  // Re-render polygons only when the colour ramp actually shifts. Using
  // primitive min/max as deps avoids redrawing on every pan: panning gives
  // metricByCode a fresh object reference (new visible commune codes) but
  // the min/max usually stay put — and newly-loaded tiles already pick up
  // the latest metricByCode through the styleFor refs above. Without this,
  // VectorGrid wipes its tile cache and re-fetches the whole viewport on
  // every drag.
  useEffect(() => {
    const layer = layerRef.current;
    if (layer && typeof layer.redraw === "function") {
      layer.redraw();
    }
  }, [range?.min, range?.max]);

  return null;
}
