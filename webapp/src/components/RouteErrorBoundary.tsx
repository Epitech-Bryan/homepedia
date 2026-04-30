import { useCallback } from "react";
import { ErrorBoundary, type FallbackProps } from "react-error-boundary";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Wraps every lazy-loaded route. If anything inside the route subtree throws —
 * a render error, a TanStack Query error promoted to throw, a chunk-load
 * failure — the boundary swaps the broken UI for a recoverable card instead
 * of a white screen. resetErrorBoundary() retries the render; routing back
 * home is offered as an escape hatch.
 *
 * <p>
 * Errors are logged to the console for now. Sentry/GlitchTip wiring would
 * plug into onError below.
 */
function Fallback({ error, resetErrorBoundary }: FallbackProps) {
  const navigate = useNavigate();
  const goHome = useCallback(() => {
    resetErrorBoundary();
    navigate("/");
  }, [navigate, resetErrorBoundary]);

  return (
    <div className="flex min-h-[60vh] items-center justify-center p-6">
      <div className="max-w-md rounded-lg border border-destructive/30 bg-destructive/5 p-6 text-sm">
        <div className="mb-3 flex items-center gap-2 text-destructive">
          <AlertTriangle className="h-4 w-4" />
          <span className="font-semibold">Une erreur est survenue</span>
        </div>
        <p className="mb-4 text-muted-foreground">
          {error instanceof Error ? error.message : "Erreur inconnue"}
        </p>
        <div className="flex gap-2">
          <Button size="sm" variant="default" onClick={resetErrorBoundary}>
            <RotateCcw className="mr-1 h-3 w-3" /> Réessayer
          </Button>
          <Button size="sm" variant="outline" onClick={goHome}>
            Retour à l'accueil
          </Button>
        </div>
      </div>
    </div>
  );
}

export function RouteErrorBoundary({ children }: { children: React.ReactNode }) {
  return (
    <ErrorBoundary
      FallbackComponent={Fallback}
      onError={(error, info) => {
        console.error("Route error caught by boundary", error, info);
      }}
    >
      {children}
    </ErrorBoundary>
  );
}
