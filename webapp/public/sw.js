/**
 * Service worker for homepedia — single-purpose cache for the vector-tile
 * endpoint. Leaflet keeps tiles in an in-memory LRU which is wiped on every
 * page refresh; this SW persists them to the browser's CacheStorage so a
 * user revisiting the app pays 0 network for tiles they've already seen.
 *
 * Strategy: stale-while-revalidate.
 *   1. Return the cached response immediately (instant pan, no flicker).
 *   2. Refresh the cache in the background from the network so the next
 *      visit picks up any changes from a tile rebuild.
 *
 * Bump CACHE_VERSION when the tile schema or properties change in a way
 * that the old cached tiles must NOT be served. Routine rebuilds (same
 * mbtiles shape, refreshed stats) are picked up transparently via SWR.
 */
const CACHE_VERSION = "v1";
const TILE_CACHE = `homepedia-tiles-${CACHE_VERSION}`;
const TILE_PATTERN = /^\/api\/tiles\/cities\/\d+\/\d+\/\d+\.pbf$/;

self.addEventListener("install", () => {
  // Take over the moment the SW is parsed. No precache step — the cache
  // populates on demand as the user pans.
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  // Drop caches from older versions so we don't accumulate stale tile
  // schemas after a CACHE_VERSION bump.
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(
        keys.filter((k) => k.startsWith("homepedia-tiles-") && k !== TILE_CACHE).map((k) => caches.delete(k)),
      );
      await self.clients.claim();
    })(),
  );
});

self.addEventListener("fetch", (event) => {
  // Only intercept GETs for our tile endpoint. Everything else (HTML, JS,
  // API stats calls) goes through the normal browser cache so we don't
  // risk shadowing a hot-reload or an auth header.
  if (event.request.method !== "GET") return;
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;
  if (!TILE_PATTERN.test(url.pathname)) return;

  event.respondWith(
    (async () => {
      const cache = await caches.open(TILE_CACHE);
      const cached = await cache.match(event.request);
      // Fire the network request unconditionally so the cache stays fresh,
      // but don't await it on the hot path.
      const networkPromise = fetch(event.request)
        .then((res) => {
          // 200 = real tile, 204 = empty tile (TileController convention) —
          // both worth caching so the next pan doesn't hit the network.
          if (res.status === 200 || res.status === 204) {
            cache.put(event.request, res.clone()).catch(() => {
              // Quota errors are non-fatal — Leaflet's in-memory cache
              // still keeps the current viewport snappy.
            });
          }
          return res;
        })
        .catch(() => null);
      if (cached) return cached;
      const network = await networkPromise;
      return network ?? new Response(null, { status: 504 });
    })(),
  );
});
