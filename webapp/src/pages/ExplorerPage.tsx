import { useState } from 'react';
import { useTransactions, useTransactionStats, useRegions, useDepartments } from '../api/hooks';
import { StatCard } from '../components/StatCard';
import { PriceChart } from '../components/PriceChart';
import { LoadingSpinner } from '../components/LoadingSpinner';
import type { DepartmentSummary, TransactionSummary } from '../api/client';

export function ExplorerPage() {
  const [filters, setFilters] = useState<Record<string, string>>({});
  const { data: regions } = useRegions();
  const { data: deptPage } = useDepartments(
    filters.regionCode ? { regionCode: filters.regionCode } : undefined,
  );
  const { data: transactions, isLoading } = useTransactions(filters);
  const { data: stats } = useTransactionStats(filters);

  const departments: DepartmentSummary[] = deptPage?._embedded
    ? (Object.values(deptPage._embedded).flat() as DepartmentSummary[])
    : [];

  const items: TransactionSummary[] = transactions?._embedded
    ? (Object.values(transactions._embedded).flat() as TransactionSummary[])
    : [];

  const updateFilter = (key: string, value: string) => {
    setFilters((prev) => {
      const next = { ...prev };
      if (value) {
        next[key] = value;
      } else {
        delete next[key];
      }
      if (key === 'regionCode') {
        delete next.departmentCode;
      }
      return next;
    });
  };

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
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Data Explorer</h1>
        <p className="mt-1 text-gray-500">Filter and analyze real estate transactions across France.</p>
      </div>

      {/* Filters */}
      <div className="rounded-xl bg-white p-6 shadow-sm border border-gray-100">
        <h2 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">Filters</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-600 mb-1">Region</label>
            <select
              value={filters.regionCode ?? ''}
              onChange={(e) => updateFilter('regionCode', e.target.value)}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none"
            >
              <option value="">All regions</option>
              {(regions ?? []).map((r) => (
                <option key={r.code} value={r.code}>{r.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-600 mb-1">Department</label>
            <select
              value={filters.departmentCode ?? ''}
              onChange={(e) => updateFilter('departmentCode', e.target.value)}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none"
            >
              <option value="">All departments</option>
              {departments.map((d) => (
                <option key={d.code} value={d.code}>{d.name} ({d.code})</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-600 mb-1">Year</label>
            <select
              value={filters.year ?? ''}
              onChange={(e) => updateFilter('year', e.target.value)}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none"
            >
              <option value="">All years</option>
              {[2024, 2023, 2022, 2021, 2020, 2019].map((y) => (
                <option key={y} value={y.toString()}>{y}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-600 mb-1">Property Type</label>
            <select
              value={filters.propertyType ?? ''}
              onChange={(e) => updateFilter('propertyType', e.target.value)}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 outline-none"
            >
              <option value="">All types</option>
              <option value="APARTMENT">Apartment</option>
              <option value="HOUSE">House</option>
              <option value="LAND">Land</option>
              <option value="COMMERCIAL">Commercial</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
        </div>
      </div>

      {/* Stats summary */}
      {stats && stats.totalTransactions > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          <StatCard label="Transactions" value={stats.totalTransactions} />
          <StatCard label="Average Price" value={stats.averagePrice} unit="€" />
          <StatCard label="Median Price" value={stats.medianPrice} unit="€" />
          <StatCard label="Avg. €/m²" value={stats.averagePricePerSqm} unit="€/m²" />
          <StatCard label="Price Range" value={`${(stats.minPrice / 1000).toFixed(0)}k - ${(stats.maxPrice / 1000).toFixed(0)}k`} unit="€" />
        </div>
      )}

      {/* Chart */}
      {chartData.length > 0 && (
        <PriceChart data={chartData} title="Price Distribution" />
      )}

      {/* Transactions list */}
      <div>
        <h2 className="text-xl font-semibold text-gray-900 mb-4">
          Transactions {transactions?.page ? `(${transactions.page.totalElements.toLocaleString('fr-FR')})` : ''}
        </h2>
        {isLoading ? (
          <LoadingSpinner />
        ) : items.length === 0 ? (
          <p className="text-gray-500 py-8 text-center">No transactions match your filters.</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {items.map((tx) => (
              <div
                key={tx.id}
                className="rounded-xl bg-white p-5 shadow-sm border border-gray-100"
              >
                <div className="flex items-start justify-between">
                  <div>
                    <p className="font-semibold text-gray-900">
                      {tx.propertyValue?.toLocaleString('fr-FR')} €
                    </p>
                    <p className="text-sm text-gray-500 mt-1">{tx.cityName}</p>
                  </div>
                  <span className="inline-flex rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-medium text-indigo-700">
                    {tx.propertyType}
                  </span>
                </div>
                <div className="mt-3 flex items-center gap-4 text-xs text-gray-400">
                  <span>{tx.mutationDate}</span>
                  {tx.builtSurface > 0 && <span>{tx.builtSurface} m²</span>}
                  {tx.roomCount > 0 && <span>{tx.roomCount} rooms</span>}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
