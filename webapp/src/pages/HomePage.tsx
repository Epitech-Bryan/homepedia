import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useRegions, useGeoRegions } from '../api/hooks';
import { FranceMap } from '../components/FranceMap';
import { StatCard } from '../components/StatCard';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorMessage } from '../components/ErrorMessage';

export function HomePage() {
  const navigate = useNavigate();
  const { data: regions, isLoading, error } = useRegions();
  const { data: geoRegions } = useGeoRegions();
  const [search, setSearch] = useState('');

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
      {/* Hero */}
      <div className="text-center">
        <h1 className="text-4xl font-bold tracking-tight text-gray-900">
          Explore the French Housing Market
        </h1>
        <p className="mt-3 text-lg text-gray-600">
          Interactive analysis of real estate data across all regions and departments of France.
        </p>
      </div>

      {/* Search */}
      <div className="max-w-md mx-auto">
        <input
          type="text"
          placeholder="Search regions..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full rounded-lg border border-gray-300 px-4 py-3 text-sm shadow-sm focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none"
        />
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard label="Regions" value={regionList.length} />
        <StatCard label="Total Population" value={totalPopulation} />
        <StatCard label="Total Area" value={totalArea} unit="km²" />
      </div>

      {/* Map */}
      <FranceMap
        geojson={geoRegions ?? null}
        onFeatureClick={(code) => navigate(`/regions/${code}`)}
        height="550px"
      />

      {/* Region cards grid */}
      <div>
        <h2 className="text-xl font-semibold text-gray-900 mb-4">All Regions</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredRegions.map((region) => (
            <button
              key={region.code}
              onClick={() => navigate(`/regions/${region.code}`)}
              className="text-left rounded-xl bg-white p-5 shadow-sm border border-gray-100 hover:border-indigo-300 hover:shadow-md transition-all"
            >
              <h3 className="font-semibold text-gray-900">{region.name}</h3>
              <div className="mt-2 flex items-center gap-4 text-sm text-gray-500">
                <span>{(region.population ?? 0).toLocaleString('fr-FR')} hab.</span>
                <span>{(region.area ?? 0).toLocaleString('fr-FR')} km²</span>
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
