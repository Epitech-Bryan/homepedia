import { lazy, Suspense, useState, useCallback } from "react";
import { useTransactions, useTransactionStats, useRegions, useDepartments } from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { LoadingSpinner } from "@/components/LoadingSpinner";

// Defer Recharts until the user has actually scrolled to results that need
// it — the explorer's first paint is the filter panel + map, which don't
// need the chart bundle.
const PriceChart = lazy(() =>
  import("@/components/PriceChart").then((m) => ({ default: m.PriceChart })),
);
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
import { TransactionDetailDialog } from "@/components/TransactionDetailDialog";
import type { TransactionSummary } from "@/api/client";

const PAGE_SIZE = 20;

// Sentinel used as Select value when the filter is cleared. Base UI's
// SelectItem requires a non-empty string value, so we can't pass "" — and
// undefined would lose Select's controlled-component contract. The render
// helpers below map this back to a human label inside the trigger.
const ALL = "__all__";

const PROPERTY_TYPE_LABELS: Record<string, string> = {
  MAISON: "Maison",
  APPARTEMENT: "Appartement",
  DEPENDANCE: "Dépendance",
  LOCAL: "Local",
};

export function ExplorerPage() {
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);
  const [selectedTxId, setSelectedTxId] = useState<number | null>(null);
  const { data: regions } = useRegions();
  const { data: departments = [] } = useDepartments(
    filters.regionCode ? { regionCode: filters.regionCode } : undefined,
  );
  const { data: transactions, isLoading } = useTransactions({
    ...filters,
    page: page.toString(),
    size: PAGE_SIZE.toString(),
  });
  const { data: stats } = useTransactionStats(filters);

  const items: TransactionSummary[] = transactions?._embedded
    ? (Object.values(transactions._embedded).flat() as TransactionSummary[])
    : [];

  const updateFilter = useCallback((key: string, value: string | null) => {
    setPage(0);
    setFilters((prev) => {
      const next = Object.fromEntries(Object.entries(prev).filter(([k]) => k !== key));
      if (value && value !== ALL) {
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
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-bold tracking-tight">Data Explorer</h1>
        <p className="text-muted-foreground text-sm">Filter and analyze transactions.</p>
      </div>

      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-xs uppercase tracking-wide">Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="space-y-1 sm:col-span-2">
              <label className="text-xs font-medium text-muted-foreground">Region</label>
              <Select
                value={filters.regionCode ?? ALL}
                onValueChange={(v) => updateFilter("regionCode", v)}
              >
                <SelectTrigger className="w-full h-8 text-xs">
                  <SelectValue placeholder="All regions">
                    {(v: string) =>
                      v === ALL ? "All regions" : (regions?.find((r) => r.code === v)?.name ?? v)
                    }
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All regions</SelectItem>
                  {(regions ?? []).map((r) => (
                    <SelectItem key={r.code} value={r.code}>
                      {r.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1 sm:col-span-2">
              <label className="text-xs font-medium text-muted-foreground">Department</label>
              <Select
                value={filters.departmentCode ?? ALL}
                onValueChange={(v) => updateFilter("departmentCode", v)}
              >
                <SelectTrigger className="w-full h-8 text-xs">
                  <SelectValue placeholder="All depts">
                    {(v: string) => {
                      if (v === ALL) return "All departments";
                      const d = departments.find((dep) => dep.code === v);
                      return d ? `${d.name} (${v})` : v;
                    }}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All departments</SelectItem>
                  {departments.map((d) => (
                    <SelectItem key={d.code} value={d.code}>
                      {d.name} ({d.code})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Year</label>
              <Select value={filters.year ?? ALL} onValueChange={(v) => updateFilter("year", v)}>
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue placeholder="All years">
                    {(v: string) => (v === ALL ? "All years" : v)}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All years</SelectItem>
                  {[2024, 2023, 2022, 2021, 2020, 2019].map((y) => (
                    <SelectItem key={y} value={y.toString()}>
                      {y}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Type</label>
              <Select
                value={filters.propertyType ?? ALL}
                onValueChange={(v) => updateFilter("propertyType", v)}
              >
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue placeholder="All types">
                    {(v: string) => (v === ALL ? "All types" : (PROPERTY_TYPE_LABELS[v] ?? v))}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All types</SelectItem>
                  {Object.entries(PROPERTY_TYPE_LABELS).map(([code, label]) => (
                    <SelectItem key={code} value={code}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {stats && stats.totalTransactions > 0 && (
        <div className="grid grid-cols-2 gap-3">
          <StatCard label="Transactions" value={stats.totalTransactions} />
          <StatCard label="Avg. Price" value={stats.averagePrice} unit="€" />
          <StatCard label="Median" value={stats.medianPrice} unit="€" />
          <StatCard label="Avg. €/m²" value={stats.averagePricePerSqm} unit="€/m²" />
        </div>
      )}

      {chartData.length > 0 && (
        <Suspense fallback={null}>
          <PriceChart data={chartData} title="Price Distribution" />
        </Suspense>
      )}

      <div>
        <h2 className="text-sm font-semibold mb-2">
          Transactions{" "}
          {transactions?.page ? `(${transactions.page.totalElements.toLocaleString("fr-FR")})` : ""}
        </h2>
        {isLoading ? (
          <LoadingSpinner />
        ) : items.length === 0 ? (
          <p className="text-muted-foreground py-4 text-center text-sm">
            No transactions match your filters.
          </p>
        ) : (
          <>
            <div className="space-y-2">
              {items.map((tx) => (
                <Card key={tx.id} className="cursor-pointer" onClick={() => setSelectedTxId(tx.id)}>
                  <CardContent className="py-3">
                    <div className="flex items-start justify-between">
                      <div>
                        <p className="font-semibold text-sm">
                          {tx.propertyValue?.toLocaleString("fr-FR")} €
                        </p>
                        <p className="text-xs text-muted-foreground mt-0.5">{tx.cityName}</p>
                      </div>
                      {tx.propertyType && <Badge className="text-xs">{tx.propertyType}</Badge>}
                    </div>
                    <div className="mt-2 flex items-center gap-3 text-xs text-muted-foreground">
                      <span>{tx.mutationDate}</span>
                      {tx.builtSurface > 0 && <span>{tx.builtSurface} m²</span>}
                      {tx.roomCount > 0 && <span>{tx.roomCount} rooms</span>}
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-4 pt-3">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <span className="text-xs text-muted-foreground">
                  {page + 1} / {totalPages}
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
        transactionId={selectedTxId}
        open={selectedTxId !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedTxId(null);
        }}
      />
    </div>
  );
}
