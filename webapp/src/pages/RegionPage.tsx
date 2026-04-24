import { useParams, useNavigate, Link } from 'react-router-dom';
import { useRegion, useDepartments, useGeoDepartments, useTransactionStats } from '@/api/hooks';
import { FranceMap } from '@/components/FranceMap';
import { StatCard } from '@/components/StatCard';
import { LoadingSpinner } from '@/components/LoadingSpinner';
import { ErrorMessage } from '@/components/ErrorMessage';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator } from '@/components/ui/breadcrumb';
import type { DepartmentSummary } from '@/api/client';

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
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem><BreadcrumbLink render={<Link to="/" />}>France</BreadcrumbLink></BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem><BreadcrumbPage>{region.name}</BreadcrumbPage></BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <div>
        <h1 className="text-3xl font-bold tracking-tight">{region.name}</h1>
        <p className="text-muted-foreground mt-1">Region code: {region.code}</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Population" value={region.population ?? 0} />
        <StatCard label="Area" value={region.area ?? 0} unit="km²" />
        <StatCard label="Departments" value={departments.length} />
        {stats && stats.averagePrice > 0 && (
          <StatCard label="Avg. Transaction" value={stats.averagePrice} unit="€" />
        )}
      </div>

      <FranceMap
        geojson={geoDepts ?? null}
        onFeatureClick={(deptCode) => navigate(`/departments/${deptCode}`)}
        height="450px"
      />

      <div>
        <h2 className="text-xl font-semibold mb-4">Departments ({departments.length})</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {departments.map((dept) => (
            <Card
              key={dept.code}
              className="cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => navigate(`/departments/${dept.code}`)}
            >
              <CardContent className="pt-6">
                <h3 className="font-semibold">{dept.name}</h3>
                <p className="text-sm text-muted-foreground mt-1">
                  <Badge variant="outline" className="mr-2">{dept.code}</Badge>
                  {(dept.population ?? 0).toLocaleString('fr-FR')} hab.
                </p>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}
