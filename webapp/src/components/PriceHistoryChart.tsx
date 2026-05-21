import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { QuarterlyPricePoint } from "@/api/client";

interface PriceHistoryChartProps {
  data: QuarterlyPricePoint[];
}

/**
 * Per-commune quarterly price/m² trend. Source is the
 * {@code /api/cities/{insee}/price-history} endpoint (issue #9), which
 * reads from the {@code city_price_quarterly_stats} pre-aggregate built
 * by {@code CityQuarterlyPriceAggregator} after each DVF import.
 *
 * <p>
 * Drops null / zero points so a quarter with no MAISON/APPARTEMENT sales
 * doesn't draw a misleading line down to 0. The empty state lives in the
 * parent — we trust the caller to only mount the chart when there is at
 * least a handful of usable points.
 */
export function PriceHistoryChart({ data }: PriceHistoryChartProps) {
  const points = data
    .filter((p) => p.averagePricePerSqm != null && p.averagePricePerSqm > 0)
    .map((p) => ({
      label: `${p.year} Q${p.quarter}`,
      pricePerSqm: Math.round(p.averagePricePerSqm as number),
    }));

  if (points.length < 2) {
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Évolution du prix au m²</CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={260}>
          <LineChart data={points} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
            <XAxis dataKey="label" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
            <YAxis
              tick={{ fontSize: 11 }}
              tickFormatter={(v: number) => `${(v / 1000).toFixed(1)}k`}
              domain={["auto", "auto"]}
            />
            <Tooltip
              formatter={(v) => [`${Number(v).toLocaleString("fr-FR")} €/m²`, "Prix moyen"]}
            />
            <Line
              type="monotone"
              dataKey="pricePerSqm"
              stroke="hsl(var(--primary))"
              strokeWidth={2}
              dot={{ r: 2 }}
              activeDot={{ r: 4 }}
            />
          </LineChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}
