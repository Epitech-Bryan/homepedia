// Single entry point that guarantees `leaflet.vectorgrid` is loaded against
// the same Leaflet instance the rest of the app uses. The two imports are
// hoisted in lexical order by the ESM loader:
//
//   1. `./leaflet-global` runs first → sets `globalThis.L = L`.
//   2. `leaflet.vectorgrid` runs second → its bundled code reads the global
//      `L` we just published and attaches `L.vectorGrid` on it.
//
// Callers should import this module instead of `"leaflet.vectorgrid"`
// directly, otherwise the side-effect attachment order is not guaranteed.

import "./leaflet-global";
import "leaflet.vectorgrid";

export { default } from "leaflet";
