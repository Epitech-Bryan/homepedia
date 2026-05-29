import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import type { MapStyle } from "./FranceMap";
import { fetchOsmPois, roundBbox, type OsmPoiType } from "@/api/osm";

const POI_COLOR: Record<OsmPoiType, string> = {
  museum: "#a855f7",
  station: "#0ea5e9",
  school: "#f59e0b",
  hospital: "#ef4444",
  park: "#10b981",
  attraction: "#ec4899",
};
const POI_LABEL: Record<OsmPoiType, string> = {
  museum: "Musée",
  station: "Gare",
  school: "École",
  hospital: "Hôpital",
  park: "Parc",
  attraction: "Attraction",
};
const POI_MIN_ZOOM = 12;

type MapMetricKey =
  | "population"
  | "density"
  | "gdpPerCapita"
  | "averagePrice"
  | "averagePricePerSqm"
  | "transactionCount"
  | "pollution";

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
  precisionHeatPoints?: Array<{ latitude: number; longitude: number; value: number }>;
  transactionMarkers?: Array<{
    id: number;
    latitude: number;
    longitude: number;
    propertyValue: number;
    propertyType: string;
    builtSurface: number | null;
    roomCount: number | null;
    mutationDate: string;
  }>;
  height?: string;
  onZoomChange?: (zoom: number) => void;
  onCenterChange?: (lat: number, lng: number) => void;
  onBoundsChange?: (south: number, west: number, north: number, east: number) => void;
}

const CHOROPLETH_SCALE = [
  "#fef0d9",
  "#fdd49e",
  "#fdbb84",
  "#fc8d59",
  "#ef6548",
  "#d7301f",
  "#990000",
];
const NO_DATA_FILL = "#e5e7eb";
const HOVER_LINE = "#1f2937";

const LAYER_DEFS = [
  { id: "w-admin1-fill", source: "world", sourceLayer: "admin1", minzoom: 0, maxzoom: 9 },
  { id: "w-admin2-fill", source: "world", sourceLayer: "admin2", minzoom: 7, maxzoom: 11 },
  { id: "c-regions-fill", source: "cities", sourceLayer: "regions", minzoom: 0, maxzoom: 7 },
  {
    id: "c-departments-fill",
    source: "cities",
    sourceLayer: "departments",
    minzoom: 7,
    maxzoom: 9,
  },
  { id: "c-cities-fill", source: "cities", sourceLayer: "cities", minzoom: 9, maxzoom: 22 },
] as const;

const RASTER_TILES: Record<string, string[]> = {
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

type Expr = unknown[];

function numProp(prop: string): Expr {
  return ["to-number", ["get", prop], 0];
}

function areaExpr(): Expr {
  return ["max", ["to-number", ["coalesce", ["get", "areaKm2"], ["get", "area"], 0], 0], 0];
}

function metricValueExpr(metric: MapMetricKey): Expr {
  switch (metric) {
    case "density":
      return ["case", [">", areaExpr(), 0], ["/", numProp("population"), areaExpr()], 0];
    case "gdpPerCapita":
      return numProp("gdpPerCapita");
    case "pollution":
      return numProp("pollutionScore");
    default:
      return numProp(metric);
  }
}

function metricHasValueExpr(metric: MapMetricKey): Expr {
  switch (metric) {
    case "density":
      return ["all", ["!=", ["get", "population"], ["literal", null]], [">", areaExpr(), 0]];
    case "gdpPerCapita":
      return ["!=", ["get", "gdpPerCapita"], ["literal", null]];
    case "pollution":
      return ["!=", ["get", "pollutionScore"], ["literal", null]];
    default:
      return ["!=", ["get", metric], ["literal", null]];
  }
}

function deriveRange(values: Array<number | null | undefined>): {
  min: number;
  max: number;
  breaks: number[];
} | null {
  const nums = values
    .filter((v): v is number => typeof v === "number" && Number.isFinite(v))
    .sort((a, b) => a - b);
  if (nums.length < 2) return null;
  const min = nums[0];
  const max = nums[nums.length - 1];
  if (max <= min) return null;
  const breaks: number[] = [];
  for (let i = 1; i <= 6; i++) {
    const q = nums[Math.floor((nums.length - 1) * (i / 7))];
    breaks.push(q);
  }
  return { min, max, breaks };
}

function colorExpr(
  metric: MapMetricKey,
  range: { min: number; max: number; breaks?: number[] } | null,
): Expr {
  if (!range || range.max <= range.min) return ["literal", NO_DATA_FILL];
  const value = metricValueExpr(metric);
  let ramp: Expr;
  const ascendingBreaks = (range.breaks ?? []).filter(
    (b, i, arr) => Number.isFinite(b) && (i === 0 || b > arr[i - 1]),
  );
  if (ascendingBreaks.length > 0) {
    const stops: unknown[] = ["step", value, CHOROPLETH_SCALE[0]];
    ascendingBreaks.forEach((b, i) => {
      stops.push(b, CHOROPLETH_SCALE[Math.min(i + 1, CHOROPLETH_SCALE.length - 1)]);
    });
    ramp = stops;
  } else {
    const stops: unknown[] = ["interpolate", ["linear"], value];
    CHOROPLETH_SCALE.forEach((c, i) => {
      stops.push(range.min + ((range.max - range.min) * i) / (CHOROPLETH_SCALE.length - 1), c);
    });
    ramp = stops;
  }
  return ["case", metricHasValueExpr(metric), ramp, NO_DATA_FILL];
}

export default function FranceMapGL({
  metricKey = "population",
  metricFromFeature,
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
    };
  });

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
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
    mapRef.current = map;
    map.addControl(
      new maplibregl.NavigationControl({ showCompass: true, visualizePitch: true }),
      "bottom-right",
    );
    map.dragRotate.disable();
    map.touchZoomRotate.disableRotation();

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
    map.on("idle", () => {
      recolor(map, cbRef.current.metricKey);
      if (cbRef.current.mapStyle === "bubbles") recomputeBubbles(map, cbRef.current.metricKey);
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

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const update = () => {
      const src = map.getSource("heat") as maplibregl.GeoJSONSource | undefined;
      if (!src) return;
      const pts = precisionHeatPoints ?? [];
      const maxV = pts.reduce((m, p) => Math.max(m, p.value), 0) || 1;
      src.setData({
        type: "FeatureCollection",
        features: pts.map((p) => ({
          type: "Feature",
          geometry: { type: "Point", coordinates: [p.longitude, p.latitude] },
          properties: { w: p.value / maxV },
        })),
      });
    };
    if (map.isStyleLoaded()) update();
    else map.once("idle", update);
  }, [precisionHeatPoints]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const update = () => {
      const src = map.getSource("txns") as maplibregl.GeoJSONSource | undefined;
      if (!src) return;
      const rows = transactionMarkers ?? [];
      src.setData({
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
      });
    };
    if (map.isStyleLoaded()) update();
    else map.once("idle", update);
  }, [transactionMarkers]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const apply = () => {
      const showFills = mapStyle !== "heat" && mapStyle !== "bubbles";
      const showHeat = mapStyle === "heat" || mapStyle === "all";
      const showBubbles = mapStyle === "bubbles";
      for (const d of LAYER_DEFS) {
        for (const id of [d.id, `${d.id}-line`]) {
          if (map.getLayer(id)) {
            map.setLayoutProperty(id, "visibility", showFills ? "visible" : "none");
          }
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
      for (const d of LAYER_DEFS) {
        if (!map.getLayer(d.id)) {
          map.addLayer({
            id: d.id,
            type: "fill",
            source: d.source,
            "source-layer": d.sourceLayer,
            minzoom: d.minzoom,
            maxzoom: d.maxzoom,
            paint: {
              "fill-color": NO_DATA_FILL,
              "fill-opacity": [
                "case",
                ["boolean", ["feature-state", "hover"], false],
                0.9,
                0.62,
              ] as maplibregl.ExpressionSpecification,
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
              "line-color": [
                "case",
                ["boolean", ["feature-state", "hover"], false],
                HOVER_LINE,
                "#7c2d12",
              ] as maplibregl.ExpressionSpecification,
              "line-width": [
                "case",
                ["boolean", ["feature-state", "hover"], false],
                2,
                0.5,
              ] as maplibregl.ExpressionSpecification,
              "line-opacity": 0.7,
            },
          });
        }
      }

      if (!map.getSource("heat")) {
        map.addSource("heat", { type: "geojson", data: emptyFC() });
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
        map.addSource("poi", { type: "geojson", data: emptyFC() });
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
        map.addSource("txns", { type: "geojson", data: emptyFC() });
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
        map.addSource("bubbles", { type: "geojson", data: emptyFC() });
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

      recolor(map, metricKey);
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

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const apply = () => {
      const src = map.getSource("poi") as maplibregl.GeoJSONSource | undefined;
      if (!src) return;
      const rows = poiData ?? [];
      src.setData({
        type: "FeatureCollection",
        features: rows.map((p) => ({
          type: "Feature",
          geometry: { type: "Point", coordinates: [p.lon, p.lat] },
          properties: { name: p.name, type: p.type },
        })),
      });
    };
    if (map.isStyleLoaded()) apply();
    else map.once("idle", apply);
  }, [poiData]);

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

function emptyFC(): GeoJSON.FeatureCollection {
  return { type: "FeatureCollection", features: [] };
}

function num(x: unknown): number | null {
  const n = Number(x);
  return Number.isFinite(n) ? n : null;
}

function jsMetricValue(props: Record<string, unknown>, metric: MapMetricKey): number | null {
  switch (metric) {
    case "density": {
      const pop = num(props.population);
      const area = num(props.areaKm2 ?? props.area);
      return pop != null && area != null && area > 0 ? pop / area : null;
    }
    case "gdpPerCapita": {
      const g = num(props.gdpPerCapita);
      if (g != null) return g;
      const gn = num(props.gdpNominal);
      const pop = num(props.population);
      return gn != null && pop != null && pop > 0 ? gn / pop : null;
    }
    case "pollution":
      return num(props.pollutionScore);
    default:
      return num(props[metric]);
  }
}

function recolor(map: maplibregl.Map, metricKey: MapMetricKey) {
  for (const d of LAYER_DEFS) {
    if (!map.getLayer(d.id)) continue;
    const feats = map.queryRenderedFeatures({ layers: [d.id] });
    const values = feats.map((f) =>
      jsMetricValue(f.properties as Record<string, unknown>, metricKey),
    );
    const range = deriveRange(values);
    map.setPaintProperty(
      d.id,
      "fill-color",
      colorExpr(metricKey, range) as maplibregl.ExpressionSpecification,
    );
  }
}

function collectCoords(geom: GeoJSON.Geometry, out: number[][]): void {
  const walk = (c: unknown): void => {
    if (Array.isArray(c) && typeof c[0] === "number") {
      out.push(c as number[]);
    } else if (Array.isArray(c)) {
      for (const child of c) walk(child);
    }
  };
  if ("coordinates" in geom) walk((geom as { coordinates: unknown }).coordinates);
}

function recomputeBubbles(map: maplibregl.Map, metricKey: MapMetricKey) {
  const src = map.getSource("bubbles") as maplibregl.GeoJSONSource | undefined;
  if (!src) return;
  const layers = LAYER_DEFS.map((d) => d.id).filter((id) => map.getLayer(id));
  const feats = layers.length ? map.queryRenderedFeatures({ layers }) : [];
  const agg = new Map<string, { sx: number; sy: number; n: number; value: number; name: string }>();
  for (const f of feats) {
    const props = f.properties ?? {};
    const code = String(props.code ?? "");
    if (!code) continue;
    const value = jsMetricValue(props as Record<string, unknown>, metricKey);
    if (value == null || value <= 0) continue;
    const coords: number[][] = [];
    collectCoords(f.geometry, coords);
    if (!coords.length) continue;
    let sx = 0;
    let sy = 0;
    for (const [x, y] of coords) {
      sx += x;
      sy += y;
    }
    const e = agg.get(code);
    if (e) {
      e.sx += sx;
      e.sy += sy;
      e.n += coords.length;
    } else {
      agg.set(code, {
        sx,
        sy,
        n: coords.length,
        value,
        name: String(props.nom ?? props.name ?? code),
      });
    }
  }
  const range = deriveRange([...agg.values()].map((e) => e.value));
  const features: GeoJSON.Feature[] = [];
  for (const [code, e] of agg) {
    const ratio = range
      ? Math.max(0, Math.min(1, (e.value - range.min) / (range.max - range.min)))
      : 0.5;
    features.push({
      type: "Feature",
      geometry: { type: "Point", coordinates: [e.sx / e.n, e.sy / e.n] },
      properties: { code, name: e.name, value: e.value, r: 6 + ratio * 22 },
    });
  }
  src.setData({ type: "FeatureCollection", features });
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
