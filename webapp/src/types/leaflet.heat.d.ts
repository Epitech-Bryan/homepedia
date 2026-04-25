// Side-effect import: makes this file a module so the `declare module` blocks
// below augment the existing leaflet types instead of replacing them.
import "leaflet";

declare module "leaflet.heat" {}

declare module "leaflet" {
  type HeatLatLngTuple = [number, number, number?];

  interface HeatMapOptions {
    minOpacity?: number;
    maxZoom?: number;
    max?: number;
    radius?: number;
    blur?: number;
    gradient?: Record<number, string>;
  }

  interface HeatLayer extends Layer {
    setLatLngs(latlngs: HeatLatLngTuple[]): this;
    addLatLng(latlng: HeatLatLngTuple): this;
    setOptions(options: HeatMapOptions): this;
    redraw(): this;
  }

  function heatLayer(latlngs: HeatLatLngTuple[], options?: HeatMapOptions): HeatLayer;
}
