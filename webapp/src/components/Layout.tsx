import { Outlet, Link, useLocation } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Home, Compass, Map } from "lucide-react";
import { PersistentMap } from "@/components/PersistentMap";
import { BatchEventsBanner } from "@/components/BatchEventsBanner";

const NAV_ITEMS = [
  { to: "/", label: "Home", icon: Home },
  { to: "/explorer", label: "Explorer", icon: Compass },
];

export function Layout() {
  const { pathname } = useLocation();

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <header className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex h-14 items-center justify-between">
          <Link to="/" className="flex items-center gap-2 font-bold text-lg tracking-tight">
            <Map className="h-5 w-5 text-primary" />
            Homepedia
          </Link>
          <nav className="flex items-center gap-1">
            {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
              <Button
                key={to}
                variant={pathname === to ? "secondary" : "ghost"}
                size="sm"
                render={<Link to={to} />}
              >
                <Icon className="h-4 w-4 mr-1.5" />
                {label}
              </Button>
            ))}
          </nav>
        </div>
      </header>
      <main className="flex-1 w-full pb-8 space-y-6">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6">
          <BatchEventsBanner />
        </div>
        <PersistentMap />
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <Outlet />
        </div>
      </main>
      <footer className="border-t bg-muted/40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 text-center text-sm text-muted-foreground">
          Homepedia — French Housing Market Analysis
        </div>
      </footer>
    </div>
  );
}
