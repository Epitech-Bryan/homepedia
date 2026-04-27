import { useState, useCallback } from "react";
import { useTransactions, useTransactionStats, useRegions, useDepartments } from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { PriceChart } from "@/components/PriceChart";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { TransactionMap } from "@/components/TransactionMap";
import { TransactionDetailDialog } from "@/components/TransactionDetailDialog";
import type { DepartmentSummary, TransactionSummary } from "@/api/client";

const PAGE_SIZE = 20;

export function ExplorerPage() {
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);
  const [selectedTx, setSelectedTx] = useState<TransactionSummary | null>(null);
  const { data: regions } = useRegions();
  const { data: deptPage } = useDepartments(
    filters.regionCode ? { regionCode: filters.regionCode } : undefined,
  );
  const { data: transactions, isLoading } = useTransactions({
    ...filters,
    page: page.toString(),
    size: PAGE_SIZE.toString(),
  });
  const { data: stats } = useTransactionStats(filters);

  const departments: DepartmentSummary[] = deptPage?._embedded
    ? (Object.values(deptPage._embedded).flat() as DepartmentSummary[])
    : [];

  const items: TransactionSummary[] = transactions?._embedded
    ? (Object.values(transactions._embedded).flat() as TransactionSummary[])
    : [];

  const updateFilter = useCallback((key: string, value: string | null) => {
    setPage(0);
    setFilters((prev) => {
      const next = Object.fromEntries(Object.entries(prev).filter(([k]) => k !== key));
      if (value && value !== "__all__") {
        next[key] = value;
      }
      if (key === "regionCode") {
        return Object.fromEntries(Object.entries(next).filter(([k]) => k !== "departmentCode"));
      }
      return next;
    });
  }, []);

  const totalPages = transactions?.page?.totalPages ?? 0;

  const chartData =
    stats && stats.totalTransactions > 0
      ? [
          { label: "Average", value: stats.averagePrice },
          { label: "Median", value: stats.medianPrice },
          { label: "Min", value: stats.minPrice },
          { label: "Max", value: stats.maxPrice },
        ]
      : [];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Data Explorer</h1>
        <p className="text-muted-foreground mt-1">
          Filter and analyze real estate transactions across France.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-sm uppercase tracking-wide">Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="space-y-1">
              <label className="text-sm font-medium text-muted-foreground">Region</label>
              <Select
                value={filters.regionCode ?? "__all__"}
                onValueChange={(v) => updateFilter("regionCode", v)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="All regions" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">All regions</SelectItem>
                  {(regions ?? []).map((r) => (
                    <SelectItem key={r.code} value={r.code}>
                      {r.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <label className="text-sm font-medium text-muted-foreground">Department</label>
              <Select
                value={filters.departmentCode ?? "__all__"}
                onValueChange={(v) => updateFilter("departmentCode", v)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="All departments" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">All departments</SelectItem>
                  {departments.map((d) => (
                    <SelectItem key={d.code} value={d.code}>
                      {d.name} ({d.code})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <label className="text-sm font-medium text-muted-foreground">Year</label>
              <Select
                value={filters.year ?? "__all__"}
                onValueChange={(v) => updateFilter("year", v)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="All years" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">All years</SelectItem>
                  {[2024, 2023, 2022, 2021, 2020, 2019].map((y) => (
                    <SelectItem key={y} value={y.toString()}>
                      {y}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <label className="text-sm font-medium text-muted-foreground">Property Type</label>
              <Select
                value={filters.propertyType ?? "__all__"}
                onValueChange={(v) => updateFilter("propertyType", v)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="All types" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all__">All types</SelectItem>
                  <SelectItem value="APARTMENT">Apartment</SelectItem>
                  <SelectItem value="HOUSE">House</SelectItem>
                  <SelectItem value="LAND">Land</SelectItem>
                  <SelectItem value="COMMERCIAL">Commercial</SelectItem>
                  <SelectItem value="OTHER">Other</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {stats && stats.totalTransactions > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          <StatCard label="Transactions" value={stats.totalTransactions} />
          <StatCard label="Average Price" value={stats.averagePrice} unit="€" />
          <StatCard label="Median Price" value={stats.medianPrice} unit="€" />
          <StatCard label="Avg. €/m²" value={stats.averagePricePerSqm} unit="€/m²" />
          <StatCard
            label="Price Range"
            value={`${(stats.minPrice / 1000).toFixed(0)}k - ${(stats.maxPrice / 1000).toFixed(0)}k`}
            unit="€"
          />
        </div>
      )}

      {stats && stats.totalTransactions > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm uppercase tracking-wide">Transaction Map</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <TransactionMap filters={filters} stats={stats} />
          </CardContent>
        </Card>
      )}

      {chartData.length > 0 && <PriceChart data={chartData} title="Price Distribution" />}

      <div>
        <h2 className="text-xl font-semibold mb-4">
          Transactions{" "}
          {transactions?.page ? `(${transactions.page.totalElements.toLocaleString("fr-FR")})` : ""}
        </h2>
        {isLoading ? (
          <LoadingSpinner />
        ) : items.length === 0 ? (
          <p className="text-muted-foreground py-8 text-center">
            No transactions match your filters.
          </p>
        ) : (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {items.map((tx) => (
                <Card key={tx.id} className="cursor-pointer" onClick={() => setSelectedTx(tx)}>
                  <CardContent className="pt-6">
                    <div className="flex items-start justify-between">
                      <div>
                        <p className="font-semibold">
                          {tx.propertyValue?.toLocaleString("fr-FR")} €
                        </p>
                        <p className="text-sm text-muted-foreground mt-1">{tx.cityName}</p>
                      </div>
                      <Badge>{tx.propertyType}</Badge>
                    </div>
                    <div className="mt-3 flex items-center gap-4 text-xs text-muted-foreground">
                      <span>{tx.mutationDate}</span>
                      {tx.builtSurface > 0 && <span>{tx.builtSurface} m²</span>}
                      {tx.roomCount > 0 && <span>{tx.roomCount} rooms</span>}
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-4 pt-4">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <span className="text-sm text-muted-foreground">
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            )}
          </>
        )}
      </div>

      <TransactionDetailDialog
        transaction={selectedTx}
        open={selectedTx !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedTx(null);
        }}
      />
    </div>
  );
}
