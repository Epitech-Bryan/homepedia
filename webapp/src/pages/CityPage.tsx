import { useParams, Link } from 'react-router-dom';
import { useCity, useTransactionStats } from '../api/hooks';
import { StatCard } from '../components/StatCard';
import { PriceChart } from '../components/PriceChart';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorMessage } from '../components/ErrorMessage';

export function CityPage() {
  const { code } = useParams<{ code: string }>();
  const { data: city, isLoading, error } = useCity(code!);
  const { data: stats } = useTransactionStats(code ? { cityInseeCode: code } : undefined);

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error.message} />;
  if (!city) return <ErrorMessage message="City not found" />;

  const chartData = stats && stats.totalTransactions > 0
    ? [
        { label: 'Average', value: stats.averagePrice },
        { label: 'Median', value: stats.medianPrice },
        { label: 'Min', value: stats.minPrice },
        { label: 'Max', value: stats.maxPrice },
      ]
    : [];

  return (
    <div className="space-y-8">
      <nav className="flex items-center gap-2 text-sm text-gray-500">
        <Link to="/" className="hover:text-indigo-600">France</Link>
        <span>/</span>
        <Link to={`/departments/${city.departmentCode}`} className="hover:text-indigo-600">
          Dept. {city.departmentCode}
        </Link>
        <span>/</span>
        <span className="text-gray-900 font-medium">{city.name}</span>
      </nav>

      <div>
        <h1 className="text-3xl font-bold text-gray-900">{city.name}</h1>
        <p className="mt-1 text-gray-500">
          {city.postalCode} · INSEE {city.inseeCode}
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Population" value={city.population ?? 0} />
        <StatCard label="Area" value={city.area ?? 0} unit="km²" />
        {city.latitude && city.longitude && (
          <StatCard label="Coordinates" value={`${city.latitude.toFixed(4)}, ${city.longitude.toFixed(4)}`} />
        )}
        {stats && stats.totalTransactions > 0 && (
          <StatCard label="Transactions" value={stats.totalTransactions} />
        )}
      </div>

      {stats && stats.totalTransactions > 0 && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <StatCard label="Average Price" value={stats.averagePrice} unit="€" />
            <StatCard label="Median Price" value={stats.medianPrice} unit="€" />
            <StatCard label="Avg. €/m²" value={stats.averagePricePerSqm} unit="€/m²" />
          </div>
          <PriceChart data={chartData} title="Price Overview" />
        </>
      )}
    </div>
  );
}
