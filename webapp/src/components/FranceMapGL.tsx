import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import type { MapStyle } from "./FranceMap";
import { fetchOsmPois, roundBbox, type OsmPoiType } from "@/api/osm";
import {
  type MapMetricKey,
  LAYER_DEFS,
  colorExpr,
  propRange,
  recolor,
  recomputeBubbles,
} from "./mapgl/choropleth";
import {
  type HeatPoint,
  type TransactionMarker,
  buildHeatFeatures,
  buildPoiFeatures,
  buildTransactionFeatures,
  useGeoJsonSource,
} from "./mapgl/sources";
import { POI_MIN_ZOOM, installLayers } from "./mapgl/layers";
import { RASTER_TILES, createGlobeMap } from "./mapgl/basemap";

const POI_LABEL: Record<OsmPoiType, string> = {
  museum: "Musée",
  station: "Gare",
  school: "École",
  hospital: "Hôpital",
  park: "Parc",
  attraction: "Attraction",
};

interface FranceMapGLProps {
  metricKey?: MapMetricKey;
  metricByCode?: Record<string, number | null | undefined>;
  metricFromFeature?: (props: Record<string, unknown>) => number | null | undefined;
  choroplethRange?: { min: number; max: number; breaks?: number[] } | null;
  metricLabel?: string;
  onFeatureClick?: (code: string, name?: string) => void;
  activeFeatureCode?: string;
  basemap?: "voyager" | "satellite";
  mapStyle?: MapStyle;
  precisionHeatPoints?: HeatPoint[];
  transactionMarkers?: TransactionMarker[];
  height?: string;
  onZoomChange?: (zoom: number) => void;
  onCenterChange?: (lat: number, lng: number) => void;
  onBoundsChange?: (south: number, west: number, north: number, east: number) => void;
}

export default function FranceMapGL({
  metricKey = "population",
  metricByCode,
  metricFromFeature,
  choroplethRange,
  metricLabel,
  onFeatureClick,
  basemap = "voyager",
  mapStyle = "choropleth",
  precisionHeatPoints,
  transactionMarkers,
  height = "100%",
  onZoomChange,
  onCenterChange,
  onBoundsChange,
}: FranceMapGLProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const [viewState, setViewState] = useState<{
    zoom: number;
    bounds: { south: number; west: number; north: number; east: number };
  } | null>(null);
  const [address, setAddress] = useState<string | null>(null);
  const popupRef = useRef<maplibregl.Popup | null>(null);
  const txnPopupRef = useRef<maplibregl.Popup | null>(null);
  const hoveredRef = useRef<{ source: string; sourceLayer: string; id: string } | null>(null);
  const cbRef = useRef({
    onZoomChange,
    onCenterChange,
    onBoundsChange,
    onFeatureClick,
    metricFromFeature,
    metricLabel,
    metricKey,
    mapStyle,
    metricByCode,
    choroplethRange,
  });
  useEffect(() => {
    cbRef.current = {
      onZoomChange,
      onCenterChange,
      onBoundsChange,
      onFeatureClick,
      metricFromFeature,
      metricLabel,
      metricKey,
      mapStyle,
      metricByCode,
      choroplethRange,
    };
  });

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const map = createGlobeMap(containerRef.current, basemap);
    mapRef.current = map;

    const ro = new ResizeObserver(() => map.resize());
    ro.observe(containerRef.current);
    map.once("load", () => {
      map.resize();
      map.easeTo({ zoom: map.getZoom() + 0.001, duration: 0 });
      map.triggerRepaint();
    });
    const kick = window.setTimeout(() => {
      map.resize();
      map.triggerRepaint();
    }, 350);

    popupRef.current = new maplibregl.Popup({
      closeButton: false,
      closeOnClick: false,
      className: "maplibre-hover-popup",
    });
    txnPopupRef.current = new maplibregl.Popup({ closeButton: true, closeOnClick: true });

    const emit = () => {
      const z = map.getZoom();
      const c = map.getCenter();
      const b = map.getBounds();
      cbRef.current.onZoomChange?.(z);
      cbRef.current.onCenterChange?.(c.lat, c.lng);
      cbRef.current.onBoundsChange?.(b.getSouth(), b.getWest(), b.getNorth(), b.getEast());
      setViewState({
        zoom: z,
        bounds: { south: b.getSouth(), west: b.getWest(), north: b.getNorth(), east: b.getEast() },
      });
    };
    map.on("moveend", emit);
    map.on("load", emit);

    let addrTimer: number | null = null;
    let addrAbort: AbortController | null = null;
    map.on("mousemove", (e) => {
      if (map.getZoom() < POI_MIN_ZOOM) return;
      if (addrTimer !== null) window.clearTimeout(addrTimer);
      const { lat, lng } = e.lngLat;
      addrTimer = window.setTimeout(() => {
        addrAbort?.abort();
        addrAbort = new AbortController();
        fetch(
          `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`,
          { signal: addrAbort.signal, headers: { Accept: "application/json" } },
        )
          .then((r) => (r.ok ? r.json() : null))
          .then((json: { display_name?: string } | null) => {
            if (!json?.display_name) return;
            setAddress(
              json.display_name
                .split(",")
                .map((s) => s.trim())
                .filter(Boolean)
                .slice(0, 3)
                .join(" · "),
            );
          })
          .catch(() => {});
      }, 800);
    });
    const runRecolor = () => {
      const c = cbRef.current;
      recolor(map, c.metricKey, propRange(c.choroplethRange, c.metricByCode));
      if (c.mapStyle === "bubbles") recomputeBubbles(map, c.metricKey);
    };
    map.on("idle", runRecolor);
    // Refine as soon as a vector tile for a data source finishes loading,
    // rather than waiting for the map to go fully idle (which on the globe can
    // lag behind by a second or more under the heavy world tiles).
    let recolorTimer: number | null = null;
    map.on("sourcedata", (e) => {
      if ((e.sourceId !== "world" && e.sourceId !== "cities") || !e.isSourceLoaded) return;
      if (recolorTimer !== null) window.clearTimeout(recolorTimer);
      recolorTimer = window.setTimeout(runRecolor, 120);
    });

    const FILL_LAYERS = [...LAYER_DEFS].reverse().map((d) => d.id);

    const clearHover = () => {
      if (hoveredRef.current) {
        map.setFeatureState(hoveredRef.current, { hover: false });
        hoveredRef.current = null;
      }
      popupRef.current?.remove();
      map.getCanvas().style.cursor = "";
    };

    map.on("mousemove", (e) => {
      const feats = map.queryRenderedFeatures(e.point, {
        layers: FILL_LAYERS.filter((l) => map.getLayer(l)),
      });
      if (!feats.length) {
        clearHover();
        return;
      }
      const f = feats[0];
      const code = String(f.properties?.code ?? "");
      const src = f.source as string;
      const srcLayer = f.sourceLayer as string;
      if (!code) return;
      if (
        !hoveredRef.current ||
        hoveredRef.current.id !== code ||
        hoveredRef.current.sourceLayer !== srcLayer
      ) {
        clearHover();
        hoveredRef.current = { source: src, sourceLayer: srcLayer, id: code };
        map.setFeatureState(hoveredRef.current, { hover: true });
      }
      map.getCanvas().style.cursor = "pointer";
      const name = String(f.properties?.nom ?? f.properties?.name ?? code);
      const val = cbRef.current.metricFromFeature?.(f.properties as Record<string, unknown>);
      const label = cbRef.current.metricLabel ? ` · ${cbRef.current.metricLabel}` : "";
      const valTxt = typeof val === "number" ? `<br/>${formatValue(val)}${label}` : "";
      popupRef.current
        ?.setLngLat(e.lngLat)
        .setHTML(`<strong>${escapeHtml(name)}</strong>${valTxt}`)
        .addTo(map);
    });
    map.on("mouseout", clearHover);

    map.on("click", (e) => {
      const feats = map.queryRenderedFeatures(e.point, {
        layers: FILL_LAYERS.filter((l) => map.getLayer(l)),
      });
      if (!feats.length) return;
      const f = feats[0];
      const props = f.properties ?? {};
      const code = String(props.code ?? "");
      if (!code) return;
      const name = props.nom ?? props.name;
      cbRef.current.onFeatureClick?.(code, name != null ? String(name) : undefined);
    });

    map.on("click", "txns-layer", (e) => {
      const f = e.features?.[0];
      if (!f) return;
      const p = f.properties ?? {};
      const surface = p.surface ? `${p.surface} m²` : "n/a";
      const rooms = p.rooms ? ` · ${p.rooms} p.` : "";
      const html =
        `<strong>${formatValue(Number(p.value))} €</strong><br/>` +
        `${escapeHtml(String(p.type ?? ""))} · ${surface}${rooms}<br/>` +
        `<span style="color:#6b7280">${escapeHtml(String(p.date ?? ""))}</span>`;
      txnPopupRef.current
        ?.setLngLat((f.geometry as GeoJSON.Point).coordinates as [number, number])
        .setHTML(html)
        .addTo(map);
    });

    map.on("mouseenter", "poi-layer", (e) => {
      const f = e.features?.[0];
      if (!f) return;
      const p = f.properties ?? {};
      const t = String(p.type ?? "") as OsmPoiType;
      const label = POI_LABEL[t] ?? "";
      map.getCanvas().style.cursor = "pointer";
      popupRef.current
        ?.setLngLat((f.geometry as GeoJSON.Point).coordinates as [number, number])
        .setHTML(
          `<strong>${escapeHtml(String(p.name ?? ""))}</strong><br/><span style="color:#6b7280">${label}</span>`,
        )
        .addTo(map);
    });
    map.on("mouseleave", "poi-layer", () => {
      map.getCanvas().style.cursor = "";
      popupRef.current?.remove();
    });

    map.on("mousemove", "bubbles-layer", (e) => {
      const f = e.features?.[0];
      if (!f) return;
      const p = f.properties ?? {};
      map.getCanvas().style.cursor = "pointer";
      const label = cbRef.current.metricLabel ? ` · ${cbRef.current.metricLabel}` : "";
      popupRef.current
        ?.setLngLat(e.lngLat)
        .setHTML(
          `<strong>${escapeHtml(String(p.name ?? ""))}</strong><br/>${formatValue(Number(p.value))}${label}`,
        )
        .addTo(map);
    });
    map.on("mouseleave", "bubbles-layer", () => {
      map.getCanvas().style.cursor = "";
      popupRef.current?.remove();
    });
    map.on("click", "bubbles-layer", (e) => {
      const f = e.features?.[0];
      if (!f) return;
      const p = f.properties ?? {};
      const code = String(p.code ?? "");
      if (code) cbRef.current.onFeatureClick?.(code, p.name ? String(p.name) : undefined);
    });

    return () => {
      window.clearTimeout(kick);
      if (addrTimer !== null) window.clearTimeout(addrTimer);
      if (recolorTimer !== null) window.clearTimeout(recolorTimer);
      addrAbort?.abort();
      ro.disconnect();
      map.remove();
      mapRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const src = map.getSource("basemap") as maplibregl.RasterTileSource | undefined;
    if (src) src.setTiles(RASTER_TILES[basemap]);
  }, [basemap]);

  // When the parent's choropleth range / metric values arrive or change,
  // recolour immediately rather than waiting for the next idle/sourcedata —
  // keeps the first paint instant when data lands just after mount.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.isStyleLoaded()) return;
    recolor(map, metricKey, propRange(choroplethRange, metricByCode));
  }, [metricKey, choroplethRange, metricByCode]);

  const heatFeatures = useMemo(
    () => buildHeatFeatures(precisionHeatPoints ?? []),
    [precisionHeatPoints],
  );
  useGeoJsonSource(mapRef, "heat", heatFeatures);

  const transactionFeatures = useMemo(
    () => buildTransactionFeatures(transactionMarkers ?? []),
    [transactionMarkers],
  );
  useGeoJsonSource(mapRef, "txns", transactionFeatures);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const apply = () => {
      const showFills = mapStyle !== "heat" && mapStyle !== "bubbles";
      const showHeat = mapStyle === "heat" || mapStyle === "all";
      const showBubbles = mapStyle === "bubbles";
      const fillLayerIds = LAYER_DEFS.flatMap((d) => [d.id, `${d.id}-line`]);
      for (const id of fillLayerIds) {
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
    };
    if (map.isStyleLoaded()) apply();
    else map.once("idle", apply);
  }, [mapStyle, metricKey]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const apply = () => {
      const initial = propRange(cbRef.current.choroplethRange, cbRef.current.metricByCode);
      const initialColor = colorExpr(metricKey, initial) as maplibregl.ExpressionSpecification;
      installLayers(map, initialColor);
      recolor(map, metricKey, initial);
    };

    if (map.isStyleLoaded()) apply();
    else map.once("style.load", apply);
  }, [metricKey]);

  const poiBbox = viewState && viewState.zoom >= POI_MIN_ZOOM ? roundBbox(viewState.bounds) : null;
  const { data: poiData } = useQuery({
    queryKey: ["osm", "pois", poiBbox],
    queryFn: () => (poiBbox ? fetchOsmPois(poiBbox) : Promise.resolve([])),
    enabled: !!poiBbox,
    staleTime: Infinity,
    gcTime: 30 * 60_000,
  });

  const poiFeatures = useMemo(() => buildPoiFeatures(poiData ?? []), [poiData]);
  useGeoJsonSource(mapRef, "poi", poiFeatures);

  const showAddress = !!viewState && viewState.zoom >= POI_MIN_ZOOM && address;

  return (
    <div
      ref={containerRef}
      style={{ position: "relative", width: "100%", height, background: "#abd0f0" }}
    >
      {showAddress ? (
        <div className="absolute bottom-2 left-2 z-[500] max-w-[60%] truncate rounded bg-background/90 px-2 py-1 text-xs text-foreground shadow-sm backdrop-blur pointer-events-none">
          {address}
        </div>
      ) : null}
    </div>
  );
}

function formatValue(value: number): string {
  if (Math.abs(value) >= 1_000_000) return `${(value / 1_000_000).toFixed(1)} M`;
  if (Math.abs(value) >= 1_000) return `${(value / 1_000).toFixed(0)} k`;
  return value.toLocaleString("fr-FR", { maximumFractionDigits: 1 });
}

function escapeHtml(s: string): string {
  const map: Record<string, string> = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  };
  return s.replace(/[&<>"']/g, (c) => map[c] ?? c);
}
