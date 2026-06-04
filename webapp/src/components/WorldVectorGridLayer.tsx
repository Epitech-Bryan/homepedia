import { useEffect, useRef } from "react";
import { useMap } from "react-leaflet";
// Same singleton-L import as CityVectorGridLayer — see that file's header
// comment for why we go through leaflet-vectorgrid-setup.
import L from "@/lib/leaflet-vectorgrid-setup";

interface WorldVectorGridLayerProps {
  url?: string;
  /**
   * Reads the choropleth value out of a feature's MVT properties. The
   * world tiles bake population / area / gdpNominal / gdpPerCapita into
   * the admin-1 layer; admin-2 and countries have name + code only and
   * the function falls through to a neutral fill.
   */
  metricFromFeature?: (props: Record<string, unknown>) => number | null | undefined;
  range?: { min: number; max: number; breaks?: number[] } | null;
  palette: string[];
  onFeatureClick?: (code: string, name?: string) => void;
  metricLabel?: string;
}

function formatValue(value: number): string {
  if (value >= 1_000_000) return (value / 1_000_000).toFixed(1) + "M";
  if (value >= 1_000) return (value / 1_000).toFixed(1) + "k";
  return Math.round(value).toLocaleString("fr-FR");
}

function cleanLabel(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() && value !== "NA" ? value : undefined;
}

function featureLabel(props: Record<string, unknown> | undefined | null): string | undefined {
  if (!props) return undefined;
  return cleanLabel(props.name) ?? cleanLabel(props.type) ?? cleanLabel(props.code);
}

/**
 * MVT renderer for the world-level mbtiles (countries + admin-1 + admin-2).
 * Same architecture as {@link CityVectorGridLayer}: one VectorGrid layer,
 * three styleFor closures keyed on tippecanoe layer name, refs so the
 * style callback always reads the latest metric without forcing a layer
 * remount on every pan.
 */
// Pane stays UNDER `cityVectorTiles` (zIndex 420) so that at z>=9 over
// France the city VG receives mouseover/click first. Outside France/Belgium
// the city tiles are empty (no fill drawn), so events fall through to this
// pane naturally and admin-2/admin-3 hover keeps working for DE/IT/UK/etc.
const WORLD_VG_PANE = "worldVectorTiles";

export function WorldVectorGridLayer({
  url = "/api/tiles/world/{z}/{x}/{y}.pbf",
  metricFromFeature,
  range,
  palette,
  onFeatureClick,
  metricLabel,
}: WorldVectorGridLayerProps) {
  const map = useMap();

  if (!map.getPane(WORLD_VG_PANE)) {
    const pane = map.createPane(WORLD_VG_PANE);
    pane.style.zIndex = "410";
    pane.style.pointerEvents = "auto";
  }

  const metricFromFeatureRef = useRef(metricFromFeature);
  const rangeRef = useRef(range);
  const paletteRef = useRef(palette);
  const onFeatureClickRef = useRef(onFeatureClick);
  const metricLabelRef = useRef(metricLabel);
  useEffect(() => {
    metricFromFeatureRef.current = metricFromFeature;
    rangeRef.current = range;
    paletteRef.current = palette;
    onFeatureClickRef.current = onFeatureClick;
    metricLabelRef.current = metricLabel;
  });

  const layerRef = useRef<(L.Layer & { redraw?: () => void }) | null>(null);
  useEffect(() => {
    const styleForChoroplethable = (props: Record<string, unknown>) => {
      const r = rangeRef.current;
      const p = paletteRef.current;
      const value = metricFromFeatureRef.current?.(props);
      if (value == null || !r || !Number.isFinite(value) || value <= 0) {
        return {
          fillColor: "#e5e7eb",
          fill: true,
          fillOpacity: 0.45,
          weight: 0.5,
          color: "#9ca3af",
          interactive: true,
        };
      }
      let idx: number;
      const breaks = r.breaks;
      if (breaks && breaks.length > 0) {
        idx = breaks.length;
        for (let i = 0; i < breaks.length; i++) {
          if (value <= breaks[i]) {
            idx = i;
            break;
          }
        }
        idx = Math.min(p.length - 1, idx);
      } else {
        const ratio = (value - r.min) / Math.max(r.max - r.min, 1);
        idx = Math.min(p.length - 1, Math.max(0, Math.floor(ratio * p.length)));
      }
      return {
        fillColor: p[idx],
        fill: true,
        fillOpacity: 0.65,
        weight: 0.5,
        color: "#374151",
        interactive: true,
      };
    };

    // Admin-2 has no baked metric — render with a neutral thin border so
    // it provides geographic context without competing with the admin-1
    // choropleth underneath. Same trick as the FR IRIS layer.
    const styleForAdmin2 = () => ({
      fillColor: "#ffffff",
      fill: true,
      fillOpacity: 0.05,
      weight: 0.4,
      color: "#1f2937",
      interactive: true,
    });

    type VGLayer = L.Layer & {
      on: (ev: string, cb: unknown) => VGLayer;
      redraw?: () => void;
      setFeatureStyle: (id: string, style: L.PathOptions) => void;
      resetFeatureStyle: (id: string) => void;
    };
    const layer = (
      L as unknown as {
        vectorGrid: { protobuf: (url: string, options: unknown) => VGLayer };
      }
    ).vectorGrid.protobuf(url, {
      pane: WORLD_VG_PANE,
      vectorTileLayerStyles: {
        // Countries: rendered transparent + non-interactive at the MVT
        // level. The SVG `wrappedCountries` LeafletGeoJSON underneath
        // carries the normalised metric props (population, gdpNominal,
        // density, transactionCount-via-stats) and handles the
        // choropleth + hit-test. The MVT source ships raw Natural
        // Earth fields (POP_EST / GDP_MD / ISO_A3) which don't match
        // the frontend's lookup keys — drawing them would paint a
        // gray sheet over the SVG choropleth and break every world-
        // zoom metric (transactionCount most visibly).
        countries: () => ({
          fill: false,
          stroke: false,
          interactive: false,
        }),
        // Each style fn gets (props, zoom). World MVT bands are admin1
        // z=5-7, admin2 z=8-10, admin3 z=11-12 in tippecanoe — but
        // --extend-zooms-if-still-dropping leaks lower-detail layers
        // into higher zooms when densest features get dropped at maxzoom.
        // Without zoom gates, admin1 fill (full choropleth) ends up painted
        // on top of the city VG commune polygons at z=8+, visually covering
        // them. Pin each layer to its tippecanoe band so only one administrative
        // level renders at any zoom.
        admin1: (props: Record<string, unknown>, zoom: number) =>
          zoom >= 5 && zoom <= 7
            ? styleForChoroplethable(props)
            : { fill: false, stroke: false, weight: 0, interactive: false },
        admin2: (_props: Record<string, unknown>, zoom: number) =>
          zoom >= 8 && zoom <= 10
            ? styleForAdmin2()
            : { fill: false, stroke: false, weight: 0, interactive: false },
        // admin-3 (European communes / Gemeinden) — same fill treatment as
        // admin-2 but a slightly thinner border so the two levels visually
        // nest when both appear during the z=11 transition.
        admin3: (_props: Record<string, unknown>, zoom: number) =>
          zoom >= 11
            ? {
                fillColor: "#ffffff",
                fill: true,
                fillOpacity: 0.04,
                weight: 0.3,
                color: "#374151",
                interactive: true,
              }
            : { fill: false, stroke: false, weight: 0, interactive: false },
      },
      interactive: true,
      minNativeZoom: 0,
      maxNativeZoom: 12,
      getFeatureId: (f: { properties?: { code?: string } }) => f.properties?.code,
    });

    layer.on(
      "click",
      (e: { layer: { properties?: Record<string, unknown> & { code?: string } } }) => {
        const code = e.layer?.properties?.code;
        if (code) {
          onFeatureClickRef.current?.(code, featureLabel(e.layer.properties));
        }
      },
    );

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
      const label = cleanLabel(name) ?? cleanLabel(props.type) ?? cleanLabel(props.code) ?? code;
      const value = metricFromFeatureRef.current?.(props);
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
        if (hoveredId && hoveredId !== code) layer.resetFeatureStyle(hoveredId);
        hoveredId = code;
        layer.setFeatureStyle(code, {
          fill: true,
          fillOpacity: 0.9,
          weight: 2,
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
        // Same race as the city VG: mouseover(B) → mouseout(A) on
        // adjacent admin-2 polygons would otherwise wipe B's highlight.
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
      try {
        layer.remove();
      } catch {
        // ignore
      }
    };
  }, [map, url]);

  useEffect(() => {
    const layer = layerRef.current;
    if (layer && typeof layer.redraw === "function") {
      layer.redraw();
    }
  }, [range?.min, range?.max]);

  return null;
}
