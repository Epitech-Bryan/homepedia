import { RouterProvider } from "react-router-dom";
import { PersistQueryClientProvider } from "@tanstack/react-query-persist-client";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider } from "@/auth/AuthContext";
import { queryClient, queryPersister, QUERY_CACHE_BUSTER } from "@/api/queryClient";
import { router } from "@/router";

export default function App() {
  return (
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={{
        persister: queryPersister,
        // Drop persisted entries older than 24h on hydration so a stale
        // tab doesn't show data from yesterday.
        maxAge: 24 * 60 * 60 * 1000,
        buster: QUERY_CACHE_BUSTER,
      }}
    >
      <AuthProvider>
        <TooltipProvider>
          <RouterProvider router={router} />
        </TooltipProvider>
      </AuthProvider>
    </PersistQueryClientProvider>
  );
}
