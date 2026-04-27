import { createBrowserRouter } from "react-router-dom";
import { Layout } from "@/components/Layout";
import { RegionPage } from "@/pages/RegionPage";
import { DepartmentPage } from "@/pages/DepartmentPage";
import { CityPage } from "@/pages/CityPage";
import { ExplorerPage } from "@/pages/ExplorerPage";
import { ReviewsPage } from "@/pages/ReviewsPage";
import { AdminPage } from "@/pages/AdminPage";
import { NotFoundPage } from "@/pages/NotFoundPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: null },
      { path: "regions/:code", element: <RegionPage /> },
      { path: "departments/:code", element: <DepartmentPage /> },
      { path: "cities/:code", element: <CityPage /> },
      { path: "cities/:code/reviews", element: <ReviewsPage /> },
      { path: "explorer", element: <ExplorerPage /> },
      { path: "admin", element: <AdminPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
