import { memo, useCallback, useEffect } from "react";
import {
  MapContainer,
  TileLayer,
  GeoJSON as LeafletGeoJSON,
  CircleMarker,
  Tooltip,
  useMap,
} from "react-leaflet";
import L from "leaflet";
import type { Layer, LeafletMouseEvent, PathOptions } from "leaflet";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import "leaflet/dist/leaflet.css";

const FRANCE_CENTER: [number, number] = [46.6, 2.5];
const FRANCE_ZOOM = 6;

interface FeatureProperties {
  code?: string;
  name?: string;
  nom?: string;
}

export interface MapMarker {
  id: string;
  lat: number;
  lon: number;
  name: string;
}

interface FranceMapProps {
  geojson: GeoJSON.FeatureCollection | null;
  onFeatureClick?: (code: string, name: string) => void;
  markers?: MapMarker[];
  onMarkerClick?: (id: string) => void;
  activeFeatureCode?: string;
  fillColor?: string;
  height?: string;
}

function FitBounds({
  geojson,
  activeFeatureCode,
}: {
  geojson: GeoJSON.FeatureCollection | null;
  activeFeatureCode?: string;
}) {
  const map = useMap();
  useEffect(() => {
    if (!geojson || !geojson.features?.length) {
      map.setView(FRANCE_CENTER, FRANCE_ZOOM);
      return;
    }
    let features = geojson.features;
    if (activeFeatureCode) {
      const filtered = features.filter(
        (f) => (f.properties as FeatureProperties | null)?.code === activeFeatureCode,
      );
      if (filtered.length > 0) features = filtered;
    }
    const layer = L.geoJSON({ type: "FeatureCollection", features });
    const bounds = layer.getBounds();
    if (bounds.isValid()) {
      map.flyToBounds(bounds, { padding: [24, 24], duration: 0.6 });
    }
  }, [geojson, activeFeatureCode, map]);
  return null;
}

function FranceMapComponent({
  geojson,
  onFeatureClick,
  markers,
  onMarkerClick,
  activeFeatureCode,
  fillColor = "hsl(221.2 83.2% 53.3%)",
  height = "500px",
}: FranceMapProps) {
  const defaultStyle: PathOptions = {
    fillColor,
    fillOpacity: 0.35,
    color: fillColor,
    weight: 1.5,
    opacity: 0.8,
  };

  const activeStyle: PathOptions = {
    fillColor,
    fillOpacity: 0.55,
    color: fillColor,
    weight: 2.5,
    opacity: 1,
  };

  const highlightStyle: PathOptions = {
    fillOpacity: 0.65,
    weight: 3,
    opacity: 1,
  };

  const styleFor = useCallback(
    (feature?: GeoJSON.Feature<GeoJSON.Geometry, FeatureProperties>): PathOptions => {
      const code = feature?.properties?.code;
      return activeFeatureCode && code === activeFeatureCode ? activeStyle : defaultStyle;
    },
    [activeFeatureCode, fillColor],
  );

  const onEachFeature = useCallback(
    (feature: GeoJSON.Feature<GeoJSON.Geometry, FeatureProperties>, layer: Layer) => {
      const props = feature.properties;
      const name = props?.name ?? props?.nom ?? "";
      const code = props?.code ?? "";
      const isActive = activeFeatureCode && code === activeFeatureCode;

      if (name) {
        layer.bindTooltip(name, { sticky: true });
      }

      layer.on({
        mouseover: (e: LeafletMouseEvent) => {
          const target = e.target as L.Path;
          target.setStyle(highlightStyle);
          target.bringToFront();
        },
        mouseout: (e: LeafletMouseEvent) => {
          const target = e.target as L.Path;
          target.setStyle(isActive ? activeStyle : defaultStyle);
        },
        click: () => {
          if (onFeatureClick && code) {
            onFeatureClick(code, name);
          }
        },
      });
    },
    [onFeatureClick, fillColor, activeFeatureCode],
  );

  return (
    <Card className="overflow-hidden">
      <CardContent className="p-0">
        <div style={{ height }}>
          {!geojson ? (
            <Skeleton className="w-full h-full" />
          ) : (
            <MapContainer
              center={FRANCE_CENTER}
              zoom={FRANCE_ZOOM}
              scrollWheelZoom={true}
              style={{ width: "100%", height: "100%" }}
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              <LeafletGeoJSON
                key={JSON.stringify(geojson).slice(0, 100)}
                data={geojson}
                style={styleFor}
                onEachFeature={onEachFeature}
              />
              {markers?.map((m) => (
                <CircleMarker
                  key={m.id}
                  center={[m.lat, m.lon]}
                  radius={4}
                  pathOptions={{
                    fillColor: "hsl(0 72% 51%)",
                    color: "hsl(0 72% 51%)",
                    fillOpacity: 0.85,
                    weight: 1,
                  }}
                  eventHandlers={{
                    click: () => onMarkerClick?.(m.id),
                  }}
                >
                  <Tooltip sticky>{m.name}</Tooltip>
                </CircleMarker>
              ))}
              <FitBounds geojson={geojson} activeFeatureCode={activeFeatureCode} />
            </MapContainer>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export const FranceMap = memo(FranceMapComponent);
