import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "@/App";
import { reportWebVitals } from "@/api/webVitals";
import { api } from "@/api/client";
import { queryClient } from "@/api/queryClient";
import "./index.css";

// eslint-disable-next-line @typescript-eslint/no-non-null-assertion
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// Real-User Monitoring: fires after the metrics resolve (LCP after the
// largest paint, INP after the next interaction, etc.). Doesn't block
// startup.
reportWebVitals();

// Idle-time prefetch of refdata that the map always ends up needing
// (countries on world zoom, regions/departments past zoom 5). Persisted
// to localStorage by App's PersistQueryClientProvider, so a second tab
// load resolves instantly. Wrapped in requestIdleCallback so the
// network calls don't compete with the LCP-critical bundle download;
// degrades to setTimeout on Safari which still lacks the API.
const schedule = (cb: () => void) => {
  type WithRic = Window & {
    requestIdleCallback?: (cb: IdleRequestCallback, options?: IdleRequestOptions) => number;
  };
  const w = window as WithRic;
  if (typeof w.requestIdleCallback === "function") {
    w.requestIdleCallback(cb, { timeout: 4000 });
  } else {
    setTimeout(cb, 1500);
  }
};

schedule(() => {
  queryClient.prefetchQuery({
    queryKey: ["geo", "countries"],
    queryFn: () => api.geo.countries(),
    staleTime: Infinity,
  });
  queryClient.prefetchQuery({
    queryKey: ["geo", "regions"],
    queryFn: () => api.geo.regions(),
    staleTime: Infinity,
  });
  queryClient.prefetchQuery({
    queryKey: ["regions"],
    queryFn: () => api.regions.list(),
    staleTime: 60 * 60 * 1000,
  });
});

// Disk-cache MVT vector tiles via a single-purpose service worker. Leaflet's
// in-memory cache is wiped on every refresh; persisting to CacheStorage
// drops the network round-trip for any tile the user has already seen,
// across sessions. Registration is deferred until `load` so it never
// competes with the LCP-critical bundle download. Only enabled in prod
// builds — running a SW against the Vite dev server would shadow HMR.
if ("serviceWorker" in navigator && import.meta.env.PROD) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch(() => {
      // SW registration failures are non-fatal: the app keeps working,
      // just without the disk cache for tiles.
    });
  });
}
