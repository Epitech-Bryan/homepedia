import { useParams, Link, useNavigate } from 'react-router-dom';
import { useDepartment, useCities, useTransactionStats } from '../api/hooks';
import { StatCard } from '../components/StatCard';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorMessage } from '../components/ErrorMessage';
import type { CitySummary } from '../api/client';

export function DepartmentPage() {
  const { code } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const { data: dept, isLoading, error } = useDepartment(code!);
  const { data: citiesPage } = useCities(code ? { departmentCode: code } : undefined);
  const { data: stats } = useTransactionStats(code ? { departmentCode: code } : undefined);

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error.message} />;
  if (!dept) return <ErrorMessage message="Department not found" />;

  const cities: CitySummary[] = citiesPage?._embedded
    ? (Object.values(citiesPage._embedded).flat() as CitySummary[])
    : [];

  return (
    <div className="space-y-8">
      <nav className="flex items-center gap-2 text-sm text-gray-500">
        <Link to="/" className="hover:text-indigo-600">France</Link>
        <span>/</span>
        <Link to={`/regions/${dept.regionCode}`} className="hover:text-indigo-600">Region {dept.regionCode}</Link>
        <span>/</span>
        <span className="text-gray-900 font-medium">{dept.name}</span>
      </nav>

      <div>
        <h1 className="text-3xl font-bold text-gray-900">{dept.name}</h1>
        <p className="mt-1 text-gray-500">Department {dept.code}</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Population" value={dept.population ?? 0} />
        <StatCard label="Area" value={dept.area ?? 0} unit="km²" />
        <StatCard label="Cities" value={cities.length} />
        {stats && stats.averagePrice > 0 && (
          <StatCard label="Avg. Price" value={stats.averagePrice} unit="€" />
        )}
      </div>

      {stats && stats.totalTransactions > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <StatCard label="Transactions" value={stats.totalTransactions} />
          <StatCard label="Median Price" value={stats.medianPrice ?? 0} unit="€" />
          <StatCard label="Avg. €/m²" value={stats.averagePricePerSqm ?? 0} unit="€/m²" />
        </div>
      )}

      <div>
        <h2 className="text-xl font-semibold text-gray-900 mb-4">
          Cities ({citiesPage?.page?.totalElements ?? cities.length})
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {cities.map((city) => (
            <button
              key={city.inseeCode}
              onClick={() => navigate(`/cities/${city.inseeCode}`)}
              className="text-left rounded-xl bg-white p-5 shadow-sm border border-gray-100 hover:border-indigo-300 hover:shadow-md transition-all"
            >
              <h3 className="font-semibold text-gray-900">{city.name}</h3>
              <p className="text-sm text-gray-500 mt-1">
                {city.postalCode} · {(city.population ?? 0).toLocaleString('fr-FR')} hab.
              </p>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
