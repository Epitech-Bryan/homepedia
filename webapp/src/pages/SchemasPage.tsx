import { NavLink, Outlet } from "react-router-dom";
import { Database, GitBranch, LayoutGrid, Workflow } from "lucide-react";

const TABS = [
  { to: "/schemas/architecture", label: "Architecture", Icon: LayoutGrid },
  { to: "/schemas/db", label: "Schéma BDD", Icon: Database },
  { to: "/schemas/data", label: "Données", Icon: Workflow },
  { to: "/schemas/devops", label: "DevOps & CI/CD", Icon: GitBranch },
];

export function SchemasPage() {
  return (
    <div className="h-full w-full overflow-auto bg-background">
      <div className="max-w-6xl mx-auto px-6 py-8">
        <div className="mb-6">
          <h1 className="text-2xl font-semibold tracking-tight">Schémas</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Vue d'ensemble technique du projet — architecture, base de données et pipeline DevOps.
          </p>
        </div>
        <nav className="mb-8 flex flex-wrap gap-1 border-b">
          {TABS.map(({ to, label, Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `inline-flex items-center gap-2 border-b-2 px-3 py-2 text-sm transition-colors ${
                  isActive
                    ? "border-primary text-foreground font-medium"
                    : "border-transparent text-muted-foreground hover:text-foreground hover:border-border"
                }`
              }
            >
              <Icon className="h-4 w-4" />
              {label}
            </NavLink>
          ))}
        </nav>
        <Outlet />
      </div>
    </div>
  );
}
