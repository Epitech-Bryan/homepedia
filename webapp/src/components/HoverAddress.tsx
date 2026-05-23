import { useEffect, useRef, useState } from "react";
import { useMap } from "react-leaflet";

/**
 * Reverse-geocodes the lat/lon under the cursor via Nominatim and shows
 * the result in a small floating overlay at the bottom-left of the map.
 *
 * <p>
 * Activated only past {@code minZoom} (default z=12) — below that the
 * address would be useless and Nominatim would refuse the query anyway.
 * Mousemove is debounced 800 ms so we hit the public Nominatim instance
 * at most ~1 req/s, well under its fair-use policy.
 *
 * <p>
 * No TanStack Query: the result is single-use and lifecycle-tied to the
 * cursor position. Cancelling an in-flight fetch via AbortController
 * keeps the displayed address consistent with the latest hover.
 */
export function HoverAddress({ minZoom = 12 }: { minZoom?: number }) {
  const map = useMap();
  const [zoom, setZoom] = useState(() => map.getZoom());
  const [label, setLabel] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef<number | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const onZoom = () => setZoom(map.getZoom());
    map.on("zoomend", onZoom);
    return () => {
      map.off("zoomend", onZoom);
    };
  }, [map]);

  useEffect(() => {
    if (zoom < minZoom) {
      return;
    }
    const onMove = (e: { latlng: { lat: number; lng: number } }) => {
      if (debounceRef.current !== null) window.clearTimeout(debounceRef.current);
      const { lat, lng } = e.latlng;
      debounceRef.current = window.setTimeout(() => {
        abortRef.current?.abort();
        const ac = new AbortController();
        abortRef.current = ac;
        setLoading(true);
        // Nominatim returns a `display_name` field with the full
        // breadcrumb. zoom=18 asks for street-level detail; we trim the
        // commune part client-side because the breadcrumb is typically
        // "Street, Town, Postal, State, Country" and only the first 2-3
        // are useful for an at-a-glance overlay.
        fetch(
          `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`,
          { signal: ac.signal, headers: { Accept: "application/json" } },
        )
          .then((r) => (r.ok ? r.json() : null))
          .then((json: { display_name?: string } | null) => {
            if (!json?.display_name) {
              setLabel(null);
              return;
            }
            const parts = json.display_name
              .split(",")
              .map((s) => s.trim())
              .filter(Boolean);
            setLabel(parts.slice(0, 3).join(" · "));
          })
          .catch(() => {
            // Aborted or network error — leave the previous label so the
            // overlay doesn't flicker on a flaky mousemove burst.
          })
          .finally(() => setLoading(false));
      }, 800);
    };
    const onLeave = () => {
      if (debounceRef.current !== null) window.clearTimeout(debounceRef.current);
      abortRef.current?.abort();
      setLabel(null);
      setLoading(false);
    };
    map.on("mousemove", onMove);
    map.on("mouseout", onLeave);
    return () => {
      map.off("mousemove", onMove);
      map.off("mouseout", onLeave);
      if (debounceRef.current !== null) window.clearTimeout(debounceRef.current);
      abortRef.current?.abort();
    };
  }, [map, zoom, minZoom]);

  // Hide the overlay below the gate. We don't reset `label` in the
  // effect's cleanup because eslint flags synchronous setState there;
  // the conditional render below has the same observable effect.
  if (zoom < minZoom) return null;
  if (!label && !loading) return null;
  return (
    <div className="pointer-events-none absolute bottom-3 left-3 z-[1000] max-w-[60%] rounded-md bg-background/90 px-2.5 py-1.5 text-xs shadow-sm backdrop-blur">
      {loading && !label ? (
        <span className="text-muted-foreground">Localisation…</span>
      ) : (
        <span className="text-foreground">{label}</span>
      )}
    </div>
  );
}
