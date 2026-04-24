import { createBrowserRouter } from 'react-router-dom';
import { Layout } from '@/components/Layout';
import { HomePage } from '@/pages/HomePage';
import { RegionPage } from '@/pages/RegionPage';
import { DepartmentPage } from '@/pages/DepartmentPage';
import { CityPage } from '@/pages/CityPage';
import { ExplorerPage } from '@/pages/ExplorerPage';
import { NotFoundPage } from '@/pages/NotFoundPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'regions/:code', element: <RegionPage /> },
      { path: 'departments/:code', element: <DepartmentPage /> },
      { path: 'cities/:code', element: <CityPage /> },
      { path: 'explorer', element: <ExplorerPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
