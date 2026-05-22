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
import L from "leaflet";

// leaflet.vectorgrid's `_getVectorTilePromise` chains a bare `.then()` on its
// `fetch()` without a `.catch()`. A 404 is handled (it returns `{layers:[]}`
// and the tile renders empty), but a network-level rejection — fetch aborted
// mid-pan, browser connection-limit, transient backend hiccup — bubbles out
// as `Uncaught (in promise) TypeError: Failed to fetch`. Worse, the
// rejection means `createTile`'s `.then(renderTile)` never fires, so
// `done(null, null)` is never called and Leaflet flags the tile as
// permanently loading. Treat fetch rejections the same as 404s: resolve to
// an empty tile so the layer keeps going.
type ProtobufProto = { _getVectorTilePromise?: (coords: unknown) => Promise<unknown> };
const protoHolder = (L as unknown as { VectorGrid?: { Protobuf?: { prototype: ProtobufProto } } })
  .VectorGrid?.Protobuf?.prototype;
if (protoHolder && typeof protoHolder._getVectorTilePromise === "function") {
  const original = protoHolder._getVectorTilePromise;
  protoHolder._getVectorTilePromise = function patched(coords: unknown) {
    return original.call(this, coords).catch(() => ({ layers: [] }));
  };
}

export { default } from "leaflet";
