import { useParams, Link } from 'react-router-dom';
import { useCity, useTransactionStats } from '@/api/hooks';
import { StatCard } from '@/components/StatCard';
import { PriceChart } from '@/components/PriceChart';
import { LoadingSpinner } from '@/components/LoadingSpinner';
import { ErrorMessage } from '@/components/ErrorMessage';
import { Button } from '@/components/ui/button';
import { Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator } from '@/components/ui/breadcrumb';

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
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem><BreadcrumbLink render={<Link to="/" />}>France</BreadcrumbLink></BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem><BreadcrumbLink render={<Link to={`/departments/${city.departmentCode}`} />}>Dept. {city.departmentCode}</BreadcrumbLink></BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem><BreadcrumbPage>{city.name}</BreadcrumbPage></BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <div>
        <h1 className="text-3xl font-bold tracking-tight">{city.name}</h1>
        <p className="text-muted-foreground mt-1">
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

      <div>
        <Link to={`/cities/${code}/reviews`}>
          <Button variant="outline">View Reviews &amp; Opinions</Button>
        </Link>
      </div>
    </div>
  );
}
