import { useAreaSentiment, useAreaWordCloud, useAreaReviews } from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { PollutionScore } from "@/components/PollutionScore";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

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

/**
 * Aggregated resident opinion for a geographic scope above the commune level
 * (department / region / country). `basePath` is the scope root — e.g.
 * `/regions/11`, `/departments/59` or `/country`. Mirrors the per-city sentiment
 * / word-cloud / latest-reviews blocks so the map's drill-down feels uniform at
 * every level.
 */
export function AreaReviewsSection({
  basePath,
  pollutionScore,
}: {
  basePath: string;
  pollutionScore?: number | null;
}) {
  const { data: sentiment, isPending: sentimentPending } = useAreaSentiment(basePath);
  const { data: wordCloudData } = useAreaWordCloud(basePath);
  const { data: reviewsPage, isPending: reviewsPending } = useAreaReviews(basePath, {
    page: "0",
    size: "5",
  });

  const topWords = wordCloudData
    ? Object.entries(wordCloudData)
        .sort(([, a], [, b]) => b - a)
        .slice(0, 12)
    : [];

  const previewReviews = reviewsPage?._embedded
    ? Object.values(reviewsPage._embedded).flat().slice(0, 5)
    : [];

  const totalReviews = sentiment?.totalReviews ?? reviewsPage?.page?.totalElements ?? 0;
  const positiveRatio =
    sentiment && sentiment.totalReviews > 0
      ? Math.round((sentiment.positiveCount / sentiment.totalReviews) * 100)
      : null;

  const hasNoReviews =
    !sentimentPending && !reviewsPending && totalReviews === 0 && previewReviews.length === 0;

  return (
    <div className="space-y-5">
      <PollutionScore score={pollutionScore} />

      {sentimentPending ? (
        <Skeleton className="h-28 w-full" />
      ) : (
        sentiment &&
        sentiment.totalReviews > 0 && (
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">Avis des habitants</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="grid grid-cols-3 gap-3">
                <StatCard label="Score moyen" value={sentiment.averageScore.toFixed(2)} />
                <StatCard label="Avis" value={totalReviews} />
                {positiveRatio != null && <StatCard label="Positifs" value={`${positiveRatio}%`} />}
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
                    style={{ width: `${(sentiment.neutralCount / sentiment.totalReviews) * 100}%` }}
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
        )
      )}

      {topWords.length > 0 && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Ce dont parlent les habitants</CardTitle>
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

      {(reviewsPending || previewReviews.length > 0) && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Derniers avis</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {reviewsPending ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }, (_, i) => (
                  <div key={i} className="border-l-2 border-border pl-3 space-y-1.5">
                    <Skeleton className="h-3 w-24" />
                    <Skeleton className="h-3 w-full" />
                    <Skeleton className="h-3 w-4/5" />
                  </div>
                ))}
              </div>
            ) : (
              previewReviews.map((review) => (
                <div key={review.id} className="border-l-2 border-border pl-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-medium">{review.author}</span>
                    <Badge
                      className={sentimentBadgeClass(review.sentimentLabel)}
                      variant="secondary"
                    >
                      {review.sentimentLabel}
                    </Badge>
                  </div>
                  <p className="mt-1 line-clamp-2 text-xs text-foreground/80">{review.content}</p>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      )}

      {hasNoReviews && (
        <p className="text-muted-foreground text-sm">Aucun avis disponible pour ce territoire.</p>
      )}
    </div>
  );
}
