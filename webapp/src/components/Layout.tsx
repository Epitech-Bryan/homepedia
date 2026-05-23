import { Outlet, Link, useLocation, useNavigate } from "react-router-dom";
import { Map, X, Search, Compass, ShieldCheck, LogIn, LogOut, BookOpen } from "lucide-react";
import { PersistentMap } from "@/components/PersistentMap";
import { LoginDialog } from "@/components/LoginDialog";
import { useRegions, useDepartments, useCitySearch, useWorldSearch } from "@/api/hooks";
import { useAuth } from "@/auth/AuthContext";
import { useState, useRef, useEffect, useMemo } from "react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

function QuickSearch() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const { data: regions } = useRegions();
  const { data: departments = [] } = useDepartments();

  // 200 ms debounce before hitting /api/cities — fast enough to feel live,
  // slow enough that hammering the trgm GIN index per keystroke doesn't
  // matter. Regions / departments stay in-memory so they react instantly.
  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(query.trim()), 200);
    return () => window.clearTimeout(id);
  }, [query]);

  const { data: cityPage } = useCitySearch(debounced);
  const { data: worldHits } = useWorldSearch(debounced);

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    const results: { label: string; sub: string; to: string; key: string }[] = [];
    for (const r of regions ?? []) {
      if (r.name.toLowerCase().includes(q)) {
        results.push({
          label: r.name,
          sub: "Région",
          to: `/regions/${r.code}`,
          key: `r-${r.code}`,
        });
      }
    }
    for (const d of departments) {
      if (d.name.toLowerCase().includes(q) || d.code.includes(q)) {
        results.push({
          label: d.name,
          sub: `Dépt. ${d.code}`,
          to: `/departments/${d.code}`,
          key: `d-${d.code}`,
        });
      }
    }
    // Backend city search hits cities.idx_cities_name_trgm — already case-
    // and accent-insensitive. Sort by population descending so "Lille" beats
    // "Lillemer" when both match the prefix. Regions / dept stay on top
    // since they're the typical drill-down entry point.
    const cities = [...(cityPage?._embedded?.citySummaryList ?? [])].sort(
      (a, b) => (b.population ?? 0) - (a.population ?? 0),
    );
    for (const c of cities) {
      const pop = c.population ? `${c.population.toLocaleString("fr-FR")} hab.` : null;
      const sub = [`Commune ${c.inseeCode}`, c.departmentName, pop].filter(Boolean).join(" · ");
      results.push({
        label: c.name,
        sub,
        to: `/cities/${c.inseeCode}`,
        key: `c-${c.inseeCode}`,
      });
    }
    // World admin-1 hits (Bavaria, Texas, Tokyo prefecture, etc.) come
    // last so French data stays on top — the typical user is FR-focused
    // and would be surprised if "Provence" surfaced Cape Province first.
    for (const w of worldHits ?? []) {
      if (!w.code) continue;
      const pop = w.population ? `${w.population.toLocaleString("fr-FR")} hab.` : null;
      const sub = [w.country, "admin-1 monde", pop].filter(Boolean).join(" · ");
      results.push({
        label: w.name ?? w.code,
        sub,
        to: `/world/admin1/${encodeURIComponent(w.code)}`,
        key: `w-${w.code}`,
      });
    }
    return results.slice(0, 12);
  }, [query, regions, departments, cityPage, worldHits]);

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
        placeholder="Régions, communes, monde…"
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
            <li key={m.key}>
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
  // /schemas is full-screen content (tables, diagrams) — the 420 px side
  // panel used by city/region/department pages is too narrow for the data
  // tables; render it full-bleed and hide the map underneath.
  const isFullScreen = pathname.startsWith("/schemas");
  const hasPanel = pathname !== "/" && !isFullScreen;
  const { user, logout } = useAuth();
  const [loginOpen, setLoginOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    if (pathname === "/admin") navigate("/", { replace: true });
  };

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
        <Button
          variant={pathname.startsWith("/schemas") ? "secondary" : "ghost"}
          size="sm"
          render={<Link to="/schemas" />}
        >
          <BookOpen className="h-4 w-4 mr-1.5" />
          Schémas
        </Button>
        {user && (
          <Button
            variant={pathname === "/admin" ? "secondary" : "ghost"}
            size="sm"
            render={<Link to="/admin" />}
          >
            <ShieldCheck className="h-4 w-4 mr-1.5" />
            Administrer
          </Button>
        )}
        {user ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleLogout}
            title={`Déconnecter ${user.username}`}
          >
            <LogOut className="h-4 w-4 mr-1.5" />
            {user.username}
          </Button>
        ) : (
          <Button variant="ghost" size="sm" onClick={() => setLoginOpen(true)}>
            <LogIn className="h-4 w-4 mr-1.5" />
            Connexion
          </Button>
        )}
        <LoginDialog open={loginOpen} onOpenChange={setLoginOpen} />
      </header>

      <div className="flex-1 flex overflow-hidden relative">
        {isFullScreen ? (
          <main className="flex-1 overflow-auto">
            <Outlet />
          </main>
        ) : (
          <div className={`flex-1 transition-all duration-300 ${hasPanel ? "sm:mr-[420px]" : ""}`}>
            <PersistentMap />
          </div>
        )}

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
