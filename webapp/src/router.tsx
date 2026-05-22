import { lazy, Suspense } from "react";
import { createBrowserRouter } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { Layout } from "@/components/Layout";
import { RouteErrorBoundary } from "@/components/RouteErrorBoundary";

// Code-split routes: each page becomes its own chunk so the home page
// (which is empty) doesn't have to download Admin / Explorer / chart
// libraries on first paint. Vite emits one .js per lazy boundary.
const RegionPage = lazy(() =>
  import("@/pages/RegionPage").then((m) => ({ default: m.RegionPage })),
);
const DepartmentPage = lazy(() =>
  import("@/pages/DepartmentPage").then((m) => ({ default: m.DepartmentPage })),
);
const CityPage = lazy(() => import("@/pages/CityPage").then((m) => ({ default: m.CityPage })));
const ExplorerPage = lazy(() =>
  import("@/pages/ExplorerPage").then((m) => ({ default: m.ExplorerPage })),
);
const ReviewsPage = lazy(() =>
  import("@/pages/ReviewsPage").then((m) => ({ default: m.ReviewsPage })),
);
const AdminPage = lazy(() => import("@/pages/AdminPage").then((m) => ({ default: m.AdminPage })));
const SchemasPage = lazy(() =>
  import("@/pages/SchemasPage").then((m) => ({ default: m.SchemasPage })),
);
const ArchitectureSchemaPage = lazy(() =>
  import("@/pages/schemas/ArchitectureSchemaPage").then((m) => ({
    default: m.ArchitectureSchemaPage,
  })),
);
const DbSchemaPage = lazy(() =>
  import("@/pages/schemas/DbSchemaPage").then((m) => ({ default: m.DbSchemaPage })),
);
const DevopsSchemaPage = lazy(() =>
  import("@/pages/schemas/DevopsSchemaPage").then((m) => ({ default: m.DevopsSchemaPage })),
);
const DataSchemaPage = lazy(() =>
  import("@/pages/schemas/DataSchemaPage").then((m) => ({ default: m.DataSchemaPage })),
);
const NotFoundPage = lazy(() =>
  import("@/pages/NotFoundPage").then((m) => ({ default: m.NotFoundPage })),
);

function PageFallback() {
  return (
    <div className="flex items-center justify-center p-8 text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" />
    </div>
  );
}

function withSuspense(node: React.ReactNode) {
  return (
    <RouteErrorBoundary>
      <Suspense fallback={<PageFallback />}>{node}</Suspense>
    </RouteErrorBoundary>
  );
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: null },
      { path: "regions/:code", element: withSuspense(<RegionPage />) },
      { path: "departments/:code", element: withSuspense(<DepartmentPage />) },
      { path: "cities/:code", element: withSuspense(<CityPage />) },
      { path: "cities/:code/reviews", element: withSuspense(<ReviewsPage />) },
      { path: "explorer", element: withSuspense(<ExplorerPage />) },
      { path: "admin", element: withSuspense(<AdminPage />) },
      {
        path: "schemas",
        element: withSuspense(<SchemasPage />),
        children: [
          { index: true, element: withSuspense(<ArchitectureSchemaPage />) },
          { path: "architecture", element: withSuspense(<ArchitectureSchemaPage />) },
          { path: "db", element: withSuspense(<DbSchemaPage />) },
          { path: "data", element: withSuspense(<DataSchemaPage />) },
          { path: "devops", element: withSuspense(<DevopsSchemaPage />) },
        ],
      },
      { path: "*", element: withSuspense(<NotFoundPage />) },
    ],
  },
]);
