import { QueryClient } from "@tanstack/react-query";
import { createAsyncStoragePersister } from "@tanstack/query-async-storage-persister";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      // Survive a tab refresh: data older than 24h gets dropped from the
      // persisted cache, anything fresher is served instantly while
      // React Query revalidates in the background.
      gcTime: 24 * 60 * 60 * 1000,
      retry: 1,
    },
  },
});

// localStorage persister: refdata (regions, departments, GeoJSON
// boundaries) is essentially immutable across sessions, so re-fetching
// it on every F5 is wasted bandwidth. We persist the whole cache and
// bump `buster` whenever the API contract or query keys change.
export const queryPersister = createAsyncStoragePersister({
  storage: typeof window === "undefined" ? undefined : window.localStorage,
  key: "homepedia-query-cache",
  throttleTime: 1000,
});

// Bump when the cache shape becomes incompatible (rename a query key,
// change a DTO field, etc.) so old persisted entries are dropped
// instead of deserialised into a broken shape.
export const QUERY_CACHE_BUSTER = "v1";
