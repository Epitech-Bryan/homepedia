import { useCallback } from 'react';
import Map, { Source, Layer, type MapMouseEvent } from 'react-map-gl/mapbox';
import 'mapbox-gl/dist/mapbox-gl.css';

const MAPBOX_TOKEN = (import.meta as unknown as { env: Record<string, string | undefined> }).env.VITE_MAPBOX_TOKEN;
const FRANCE_CENTER = { longitude: 2.5, latitude: 46.6, zoom: 5 };

interface FranceMapProps {
  geojson: GeoJSON.FeatureCollection | null;
  onFeatureClick?: (code: string, name: string) => void;
  fillColor?: string;
  height?: string;
}

export function FranceMap({ geojson, onFeatureClick, fillColor = '#6366f1', height = '500px' }: FranceMapProps) {
  if (!MAPBOX_TOKEN) {
    return (
      <div className="flex items-center justify-center rounded-xl bg-gray-100 border-2 border-dashed border-gray-300" style={{ height }}>
        <p className="text-gray-500 text-sm">Set VITE_MAPBOX_TOKEN to enable the map</p>
      </div>
    );
  }

  const handleClick = useCallback(
    (e: MapMouseEvent) => {
      const feature = e.features?.[0];
      if (feature && onFeatureClick) {
        const code = (feature.properties?.code as string) ?? '';
        const name = (feature.properties?.name as string) ?? '';
        onFeatureClick(code, name);
      }
    },
    [onFeatureClick],
  );

  return (
    <div className="rounded-xl overflow-hidden shadow-sm border border-gray-100" style={{ height }}>
      <Map
        initialViewState={FRANCE_CENTER}
        style={{ width: '100%', height: '100%' }}
        mapStyle="mapbox://styles/mapbox/light-v11"
        mapboxAccessToken={MAPBOX_TOKEN}
        interactiveLayerIds={geojson ? ['fill-layer'] : []}
        onClick={handleClick}
      >
        {geojson && (
          <Source type="geojson" data={geojson}>
            <Layer
              id="fill-layer"
              type="fill"
              paint={{ 'fill-color': fillColor, 'fill-opacity': 0.4 }}
            />
            <Layer
              id="line-layer"
              type="line"
              paint={{ 'line-color': fillColor, 'line-width': 1.5 }}
            />
            <Layer
              id="label-layer"
              type="symbol"
              layout={{
                'text-field': ['get', 'name'],
                'text-size': 11,
                'text-anchor': 'center',
              }}
              paint={{ 'text-color': '#1e1b4b', 'text-halo-color': '#fff', 'text-halo-width': 1 }}
            />
          </Source>
        )}
      </Map>
    </div>
  );
}
