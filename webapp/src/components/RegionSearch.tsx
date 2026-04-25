import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Input } from "@/components/ui/input";
import type { RegionSummary } from "@/api/client";

interface RegionSearchProps {
  regions: RegionSummary[];
  placeholder?: string;
  maxResults?: number;
}

export function RegionSearch({
  regions,
  placeholder = "Search regions...",
  maxResults = 8,
}: RegionSearchProps) {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return regions
      .filter((r) => r.name.toLowerCase().includes(q))
      .slice(0, maxResults);
  }, [query, regions, maxResults]);

  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [open]);

  const select = (region: RegionSummary) => {
    setQuery("");
    setOpen(false);
    navigate(`/regions/${region.code}`);
  };

  const showDropdown = open && matches.length > 0;

  return (
    <div ref={containerRef} className="relative max-w-md mx-auto">
      <Input
        type="text"
        placeholder={placeholder}
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            setOpen(false);
          } else if (e.key === "Enter" && matches.length > 0) {
            select(matches[0]);
          }
        }}
      />

      {showDropdown && (
        <ul
          role="listbox"
          className="absolute left-0 right-0 z-30 mt-1 max-h-72 overflow-auto rounded-md border bg-popover shadow-md"
        >
          {matches.map((region) => (
            <li key={region.code}>
              <button
                type="button"
                onClick={() => select(region)}
                className="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-accent hover:text-accent-foreground"
              >
                <span className="font-medium">{region.name}</span>
                <span className="text-xs text-muted-foreground">{region.code}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
