import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { SentimentStats } from "@/api/client";

interface SentimentChartProps {
  stats: SentimentStats;
}

const SENTIMENT_COLORS = {
  positive: "#22c55e",
  negative: "#ef4444",
  neutral: "#9ca3af",
};

export function SentimentChart({ stats }: SentimentChartProps) {
  const data = [
    { name: "Positive", value: stats.positiveCount },
    { name: "Negative", value: stats.negativeCount },
    { name: "Neutral", value: stats.neutralCount },
  ].filter((d) => d.value > 0);

  if (data.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Sentiment Analysis</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-sm">No data available</p>
        </CardContent>
      </Card>
    );
  }

  const colors = data.map((d) =>
    d.name === "Positive"
      ? SENTIMENT_COLORS.positive
      : d.name === "Negative"
        ? SENTIMENT_COLORS.negative
        : SENTIMENT_COLORS.neutral,
  );

  return (
    <Card>
      <CardHeader>
        <CardTitle>Sentiment Analysis</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col items-center gap-4">
          <p className="text-3xl font-bold">
            {stats.averageScore.toFixed(2)}
            <span className="ml-2 text-sm font-normal text-muted-foreground">avg. score</span>
          </p>
          <ResponsiveContainer width="100%" height={250}>
            <PieChart>
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                innerRadius={50}
                outerRadius={90}
                paddingAngle={3}
                dataKey="value"
                label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`}
              >
                {data.map((_, i) => (
                  <Cell key={i} fill={colors[i]} />
                ))}
              </Pie>
              <Tooltip />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  );
}
