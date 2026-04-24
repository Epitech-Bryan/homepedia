import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useRegions, useGeoRegions } from "@/api/hooks";
import { FranceMap } from "@/components/FranceMap";
import { StatCard } from "@/components/StatCard";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";

export function HomePage() {
  const navigate = useNavigate();
  const { data: regions, isLoading, error } = useRegions();
  const { data: geoRegions } = useGeoRegions();
  const [search, setSearch] = useState("");

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error.message} />;

  const regionList = regions ?? [];
  const totalPopulation = regionList.reduce((sum, r) => sum + (r.population ?? 0), 0);
  const totalArea = regionList.reduce((sum, r) => sum + (r.area ?? 0), 0);

  const filteredRegions = search
    ? regionList.filter((r) => r.name.toLowerCase().includes(search.toLowerCase()))
    : regionList;

  return (
    <div className="space-y-8">
      <div className="text-center space-y-2">
        <h1 className="text-4xl font-bold tracking-tight">Explore the French Housing Market</h1>
        <p className="text-lg text-muted-foreground">
          Interactive analysis of real estate data across all regions and departments of France.
        </p>
      </div>

      <div className="max-w-md mx-auto">
        <Input
          type="text"
          placeholder="Search regions..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard label="Regions" value={regionList.length} />
        <StatCard label="Total Population" value={totalPopulation} />
        <StatCard label="Total Area" value={totalArea} unit="km²" />
      </div>

      <FranceMap
        geojson={geoRegions ?? null}
        onFeatureClick={(code) => navigate(`/regions/${code}`)}
        height="550px"
      />

      <div>
        <h2 className="text-xl font-semibold mb-4">All Regions</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredRegions.map((region) => (
            <Card
              key={region.code}
              className="cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => navigate(`/regions/${region.code}`)}
            >
              <CardContent className="pt-6">
                <h3 className="font-semibold">{region.name}</h3>
                <div className="mt-2 flex items-center gap-2">
                  <Badge variant="secondary">
                    {(region.population ?? 0).toLocaleString("fr-FR")} hab.
                  </Badge>
                  <Badge variant="outline">{(region.area ?? 0).toLocaleString("fr-FR")} km²</Badge>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}
