import { Outlet, Link, useLocation, useNavigate } from "react-router-dom";
import { Map, X, Search, Compass } from "lucide-react";
import { PersistentMap } from "@/components/PersistentMap";
import { useRegions, useDepartments } from "@/api/hooks";
import { useState, useRef, useEffect, useMemo } from "react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

function QuickSearch() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const { data: regions } = useRegions();
  const { data: deptPage } = useDepartments();

  const departments = useMemo(
    () => (deptPage?._embedded ? Object.values(deptPage._embedded).flat() : []),
    [deptPage],
  );

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    const results: { label: string; sub: string; to: string }[] = [];
    for (const r of regions ?? []) {
      if (r.name.toLowerCase().includes(q)) {
        results.push({ label: r.name, sub: "Region", to: `/regions/${r.code}` });
      }
    }
    for (const d of departments) {
      if (d.name.toLowerCase().includes(q) || d.code.includes(q)) {
        results.push({ label: d.name, sub: `Dept. ${d.code}`, to: `/departments/${d.code}` });
      }
    }
    return results.slice(0, 8);
  }, [query, regions, departments]);

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

  const select = (to: string) => {
    setQuery("");
    setOpen(false);
    navigate(to);
  };

  return (
    <div ref={containerRef} className="relative w-full max-w-sm">
      <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
      <Input
        type="text"
        placeholder="Search regions, departments…"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(e) => {
          if (e.key === "Escape") setOpen(false);
          if (e.key === "Enter" && matches.length > 0) select(matches[0].to);
        }}
        className="h-8 pl-8 text-sm bg-muted/50"
      />
      {open && matches.length > 0 && (
        <ul className="absolute left-0 right-0 z-[9999] mt-1 max-h-72 overflow-auto rounded-md border bg-popover shadow-lg">
          {matches.map((m) => (
            <li key={m.to}>
              <button
                type="button"
                onClick={() => select(m.to)}
                className="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-accent hover:text-accent-foreground"
              >
                <span className="font-medium">{m.label}</span>
                <span className="text-xs text-muted-foreground">{m.sub}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function Layout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const hasPanel = pathname !== "/";

  return (
    <div className="h-screen w-screen overflow-hidden flex flex-col bg-background">
      <header className="h-12 z-[1000] flex items-center gap-3 px-4 border-b bg-background/80 backdrop-blur-lg shrink-0">
        <Link
          to="/"
          className="flex items-center gap-2 font-semibold text-sm tracking-tight shrink-0"
        >
          <Map className="h-4 w-4 text-primary" />
          <span>Homepedia</span>
        </Link>
        <div className="flex-1 flex justify-center">
          <QuickSearch />
        </div>
        <Button
          variant={pathname === "/explorer" ? "secondary" : "ghost"}
          size="sm"
          render={<Link to="/explorer" />}
        >
          <Compass className="h-4 w-4 mr-1.5" />
          Explorer
        </Button>
      </header>

      <div className="flex-1 flex overflow-hidden relative">
        <div className={`flex-1 transition-all duration-300 ${hasPanel ? "sm:mr-[420px]" : ""}`}>
          <PersistentMap />
        </div>

        {hasPanel && (
          <aside className="absolute inset-0 sm:left-auto sm:right-0 sm:w-[420px] bg-background/95 backdrop-blur-xl border-l overflow-y-auto z-40 animate-in slide-in-from-right duration-300">
            <div className="sticky top-0 z-10 flex items-center justify-between px-4 py-2 bg-background/80 backdrop-blur border-b">
              <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Details
              </span>
              <button
                onClick={() => navigate("/")}
                className="p-1 rounded-md hover:bg-muted transition-colors"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="p-4">
              <Outlet />
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}
