import { useParams, useNavigate, Link } from 'react-router-dom';
import { useRegion, useDepartments, useGeoDepartments, useTransactionStats } from '../api/hooks';
import { FranceMap } from '../components/FranceMap';
import { StatCard } from '../components/StatCard';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorMessage } from '../components/ErrorMessage';
import type { DepartmentSummary } from '../api/client';

export function RegionPage() {
  const { code } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const { data: region, isLoading, error } = useRegion(code!);
  const { data: deptPage } = useDepartments(code ? { regionCode: code } : undefined);
  const { data: geoDepts } = useGeoDepartments(code);
  const { data: stats } = useTransactionStats(code ? { regionCode: code } : undefined);

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error.message} />;
  if (!region) return <ErrorMessage message="Region not found" />;

  const departments: DepartmentSummary[] = deptPage?._embedded
    ? (Object.values(deptPage._embedded).flat() as DepartmentSummary[])
    : [];

  return (
    <div className="space-y-8">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-gray-500">
        <Link to="/" className="hover:text-indigo-600">France</Link>
        <span>/</span>
        <span className="text-gray-900 font-medium">{region.name}</span>
      </nav>

      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900">{region.name}</h1>
        <p className="mt-1 text-gray-500">Region code: {region.code}</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Population" value={region.population ?? 0} />
        <StatCard label="Area" value={region.area ?? 0} unit="km²" />
        <StatCard label="Departments" value={departments.length} />
        {stats && stats.averagePrice > 0 && (
          <StatCard label="Avg. Transaction" value={stats.averagePrice} unit="€" />
        )}
      </div>

      {/* Map */}
      <FranceMap
        geojson={geoDepts ?? null}
        onFeatureClick={(deptCode) => navigate(`/departments/${deptCode}`)}
        fillColor="#8b5cf6"
        height="450px"
      />

      {/* Departments grid */}
      <div>
        <h2 className="text-xl font-semibold text-gray-900 mb-4">
          Departments ({departments.length})
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {departments.map((dept) => (
            <button
              key={dept.code}
              onClick={() => navigate(`/departments/${dept.code}`)}
              className="text-left rounded-xl bg-white p-5 shadow-sm border border-gray-100 hover:border-purple-300 hover:shadow-md transition-all"
            >
              <h3 className="font-semibold text-gray-900">{dept.name}</h3>
              <p className="text-sm text-gray-500 mt-1">
                {dept.code} · {(dept.population ?? 0).toLocaleString('fr-FR')} hab.
              </p>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
