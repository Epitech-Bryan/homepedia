import { memo, useCallback, useEffect } from "react";
import { MapContainer, TileLayer, GeoJSON as LeafletGeoJSON, useMap } from "react-leaflet";
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

interface FranceMapProps {
  geojson: GeoJSON.FeatureCollection | null;
  onFeatureClick?: (code: string, name: string) => void;
  fillColor?: string;
  height?: string;
}

function FitBounds({ geojson }: { geojson: GeoJSON.FeatureCollection | null }) {
  const map = useMap();
  useEffect(() => {
    if (!geojson || !geojson.features?.length) {
      map.setView(FRANCE_CENTER, FRANCE_ZOOM);
      return;
    }
    const layer = L.geoJSON(geojson);
    const bounds = layer.getBounds();
    if (bounds.isValid()) {
      map.flyToBounds(bounds, { padding: [24, 24], duration: 0.6 });
    }
  }, [geojson, map]);
  return null;
}

function FranceMapComponent({
  geojson,
  onFeatureClick,
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

  const highlightStyle: PathOptions = {
    fillOpacity: 0.6,
    weight: 3,
    opacity: 1,
  };

  const onEachFeature = useCallback(
    (feature: GeoJSON.Feature<GeoJSON.Geometry, FeatureProperties>, layer: Layer) => {
      const props = feature.properties;
      const name = props?.name ?? props?.nom ?? "";
      const code = props?.code ?? "";

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
          target.setStyle(defaultStyle);
        },
        click: () => {
          if (onFeatureClick && code) {
            onFeatureClick(code, name);
          }
        },
      });
    },
    [onFeatureClick, fillColor],
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
                style={defaultStyle}
                onEachFeature={onEachFeature}
              />
              <FitBounds geojson={geojson} />
            </MapContainer>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export const FranceMap = memo(FranceMapComponent);
