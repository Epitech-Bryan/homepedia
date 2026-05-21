// Re-publish the app's Leaflet instance on `globalThis.L` so legacy plugins
// that reference `L` as a free global (Leaflet.VectorGrid, leaflet.heat, …)
// land their side-effects on the same object the rest of the app holds.
//
// Must be imported BEFORE any side-effect plugin import — the ESM module
// graph evaluates imports in lexical order, so a sibling
// `import "leaflet.vectorgrid"` placed after this one is guaranteed to see
// the global already set.

import L from "leaflet";

if (typeof globalThis !== "undefined") {
  (globalThis as unknown as { L: typeof L }).L = L;
}

export {};
