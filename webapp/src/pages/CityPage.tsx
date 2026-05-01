import { useParams, Link } from "react-router-dom";
import {
  useCity,
  useTransactionStats,
  useSentimentStats,
  useWordCloud,
  useReviews,
} from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { PriceChart } from "@/components/PriceChart";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

function sentimentBadgeClass(label: string) {
  switch (label.toLowerCase()) {
    case "positive":
      return "bg-green-500/15 text-green-700 dark:text-green-400";
    case "negative":
      return "bg-red-500/15 text-red-700 dark:text-red-400";
    default:
      return "bg-gray-500/15 text-gray-700 dark:text-gray-400";
  }
}

export function CityPage() {
  const { code = "" } = useParams<{ code: string }>();
  const { data: city, isLoading, error } = useCity(code);
  const { data: stats } = useTransactionStats(code ? { cityInseeCode: code } : undefined);
  const { data: sentiment } = useSentimentStats(code);
  const { data: wordCloudData } = useWordCloud(code);
  const { data: reviewsPage } = useReviews(code, { page: "0", size: "3" });

  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error.message} />;
  if (!city) return <ErrorMessage message="City not found" />;

  const chartData =
    stats && stats.totalTransactions > 0
      ? [
          { label: "Average", value: stats.averagePrice },
          { label: "Median", value: stats.medianPrice },
          { label: "Min", value: stats.minPrice },
          { label: "Max", value: stats.maxPrice },
        ]
      : [];

  const topWords = wordCloudData
    ? Object.entries(wordCloudData)
        .sort(([, a], [, b]) => b - a)
        .slice(0, 12)
    : [];

  const previewReviews = reviewsPage?._embedded
    ? Object.values(reviewsPage._embedded).flat().slice(0, 3)
    : [];

  const totalReviews = sentiment?.totalReviews ?? reviewsPage?.page?.totalElements ?? 0;
  const positiveRatio =
    sentiment && sentiment.totalReviews > 0
      ? Math.round((sentiment.positiveCount / sentiment.totalReviews) * 100)
      : null;

  return (
    <div className="space-y-5">
      <div>
        <p className="text-xs text-muted-foreground">
          <Link to="/" className="hover:underline">
            France
          </Link>
          {" / "}
          <Link to={`/departments/${city.departmentCode}`} className="hover:underline">
            Dept. {city.departmentCode}
          </Link>
          {" / City"}
        </p>
        <h1 className="text-xl font-bold tracking-tight mt-1">{city.name}</h1>
        <p className="text-muted-foreground text-sm">
          {city.postalCode} · INSEE {city.inseeCode}
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <StatCard label="Population" value={city.population ?? 0} />
        <StatCard label="Area" value={city.area ?? 0} unit="km²" />
        {stats && stats.totalTransactions > 0 && (
          <>
            <StatCard label="Transactions" value={stats.totalTransactions} />
            <StatCard label="Avg. €/m²" value={stats.averagePricePerSqm} unit="€/m²" />
          </>
        )}
      </div>

      {stats && stats.totalTransactions > 0 && (
        <div className="grid grid-cols-2 gap-3">
          <StatCard label="Avg. Price" value={stats.averagePrice} unit="€" />
          <StatCard label="Median" value={stats.medianPrice} unit="€" />
        </div>
      )}

      {chartData.length > 0 && <PriceChart data={chartData} title="Price Overview" />}

      {sentiment && sentiment.totalReviews > 0 && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Resident sentiment</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-3 gap-3">
              <StatCard label="Avg. Score" value={sentiment.averageScore.toFixed(2)} />
              <StatCard label="Reviews" value={totalReviews} />
              {positiveRatio != null && <StatCard label="Positive" value={`${positiveRatio}%`} />}
            </div>
            <div className="flex h-2 w-full overflow-hidden rounded-full bg-muted">
              {sentiment.positiveCount > 0 && (
                <div
                  className="bg-green-500"
                  style={{
                    width: `${(sentiment.positiveCount / sentiment.totalReviews) * 100}%`,
                  }}
                />
              )}
              {sentiment.neutralCount > 0 && (
                <div
                  className="bg-gray-400"
                  style={{
                    width: `${(sentiment.neutralCount / sentiment.totalReviews) * 100}%`,
                  }}
                />
              )}
              {sentiment.negativeCount > 0 && (
                <div
                  className="bg-red-500"
                  style={{
                    width: `${(sentiment.negativeCount / sentiment.totalReviews) * 100}%`,
                  }}
                />
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {topWords.length > 0 && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">What residents talk about</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-1.5">
              {topWords.map(([word, count]) => (
                <Badge key={word} variant="secondary" className="text-xs">
                  {word}
                  <span className="ml-1 text-muted-foreground">{count}</span>
                </Badge>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {previewReviews.length > 0 && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Latest reviews</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {previewReviews.map((review) => (
              <div key={review.id} className="border-l-2 border-border pl-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs font-medium">{review.author}</span>
                  <Badge className={sentimentBadgeClass(review.sentimentLabel)} variant="secondary">
                    {review.sentimentLabel}
                  </Badge>
                </div>
                <p className="mt-1 line-clamp-2 text-xs text-foreground/80">{review.content}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      <Link to={`/cities/${code}/reviews`}>
        <Button variant="outline" className="w-full">
          View Reviews &amp; Opinions
        </Button>
      </Link>
    </div>
  );
}
