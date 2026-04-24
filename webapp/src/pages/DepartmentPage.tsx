import { useParams, Link, useNavigate } from "react-router-dom";
import { useDepartment, useCities, useTransactionStats } from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import type { CitySummary } from "@/api/client";

export function DepartmentPage() {
  const { code = "" } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const { data: dept, isLoading, error } = useDepartment(code);
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
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink render={<Link to="/" />}>France</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbLink render={<Link to={`/regions/${dept.regionCode}`} />}>
              Region {dept.regionCode}
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{dept.name}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <div>
        <h1 className="text-3xl font-bold tracking-tight">{dept.name}</h1>
        <p className="text-muted-foreground mt-1">Department {dept.code}</p>
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
        <h2 className="text-xl font-semibold mb-4">
          Cities ({citiesPage?.page?.totalElements ?? cities.length})
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {cities.map((city) => (
            <Card
              key={city.inseeCode}
              className="cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => navigate(`/cities/${city.inseeCode}`)}
            >
              <CardContent className="pt-6">
                <h3 className="font-semibold">{city.name}</h3>
                <p className="text-sm text-muted-foreground mt-1">
                  <Badge variant="outline" className="mr-2">
                    {city.postalCode}
                  </Badge>
                  {(city.population ?? 0).toLocaleString("fr-FR")} hab.
                </p>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}
