import { useState } from "react";
import { useParams, Link } from "react-router-dom";
import { useCity, useReviews, useWordCloud, useSentimentStats } from "@/api/hooks";
import { StatCard } from "@/components/StatCard";
import { WordCloud } from "@/components/WordCloud";
import { SentimentChart } from "@/components/SentimentChart";
import { LoadingSpinner } from "@/components/LoadingSpinner";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

function sentimentBadgeVariant(label: string) {
  switch (label.toLowerCase()) {
    case "positive":
      return "default" as const;
    case "negative":
      return "destructive" as const;
    default:
      return "secondary" as const;
  }
}

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

function StarRating({ rating }: { rating: number }) {
  return (
    <span className="inline-flex gap-0.5" aria-label={`${rating} out of 5 stars`}>
      {Array.from({ length: 5 }, (_, i) => (
        <span key={i} className={i < rating ? "text-yellow-500" : "text-muted-foreground/30"}>
          ★
        </span>
      ))}
    </span>
  );
}

export function ReviewsPage() {
  const { code = "" } = useParams<{ code: string }>();
  const [page, setPage] = useState(0);

  const { data: city, isLoading: cityLoading, error: cityError } = useCity(code);
  const { data: sentiment } = useSentimentStats(code);
  const { data: wordCloudData } = useWordCloud(code);
  const { data: reviews } = useReviews(code, { page: String(page), size: "10" });

  if (cityLoading) return <LoadingSpinner />;
  if (cityError) return <ErrorMessage message={cityError.message} />;
  if (!city) return <ErrorMessage message="City not found" />;

  const reviewList = reviews?._embedded ? Object.values(reviews._embedded).flat() : [];
  const totalPages = reviews?.page?.totalPages ?? 0;

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
          {" / "}
          <Link to={`/cities/${code}`} className="hover:underline">
            {city.name}
          </Link>
          {" / Reviews"}
        </p>
        <h1 className="text-xl font-bold tracking-tight mt-1">{city.name} — Avis</h1>
      </div>

      {sentiment && (
        <div className="space-y-3">
          <SentimentChart stats={sentiment} />
          <div className="grid grid-cols-2 gap-3">
            <StatCard label="Avg. Score" value={sentiment.averageScore.toFixed(2)} />
            <StatCard label="Total Reviews" value={sentiment.totalReviews} />
          </div>
        </div>
      )}

      {wordCloudData && Object.keys(wordCloudData).length > 0 && (
        <WordCloud words={wordCloudData} />
      )}

      <div className="space-y-3">
        <h2 className="text-sm font-semibold">Reviews</h2>
        {reviewList.length === 0 ? (
          <p className="text-muted-foreground text-sm">No reviews available yet.</p>
        ) : (
          <div className="space-y-3">
            {reviewList.map((review) => (
              <Card key={review.id}>
                <CardHeader className="pb-2">
                  <CardTitle className="flex items-center justify-between flex-wrap gap-2 text-sm">
                    <div className="flex items-center gap-2">
                      <span>{review.author}</span>
                      <StarRating rating={review.rating} />
                    </div>
                    <Badge
                      className={sentimentBadgeClass(review.sentimentLabel)}
                      variant={sentimentBadgeVariant(review.sentimentLabel)}
                    >
                      {review.sentimentLabel}
                    </Badge>
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-foreground/80 leading-relaxed">{review.content}</p>
                  <p className="text-xs text-muted-foreground mt-2">
                    {new Date(review.publishedAt).toLocaleDateString("fr-FR")}
                  </p>
                </CardContent>
              </Card>
            ))}
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-4 pt-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
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
      </div>
    </div>
  );
}
