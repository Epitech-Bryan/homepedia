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
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";

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
    <div className="space-y-8">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink render={<Link to="/" />}>France</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbLink render={<Link to={`/departments/${city.departmentCode}`} />}>
              Dept. {city.departmentCode}
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbLink render={<Link to={`/cities/${code}`} />}>{city.name}</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>Reviews</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <div>
        <h1 className="text-3xl font-bold tracking-tight">{city.name} - Avis et Opinions</h1>
        <p className="text-muted-foreground mt-1">
          Discover what people think about living in {city.name}
        </p>
      </div>

      {/* Sentiment overview */}
      {sentiment && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2">
            <SentimentChart stats={sentiment} />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-1 gap-4">
            <StatCard label="Average Score" value={sentiment.averageScore.toFixed(2)} />
            <StatCard label="Total Reviews" value={sentiment.totalReviews} />
          </div>
        </div>
      )}

      {/* Word Cloud */}
      {wordCloudData && Object.keys(wordCloudData).length > 0 && (
        <WordCloud words={wordCloudData} />
      )}

      {/* Reviews list */}
      <div className="space-y-4">
        <h2 className="text-xl font-semibold tracking-tight">Reviews</h2>
        {reviewList.length === 0 ? (
          <p className="text-muted-foreground text-sm">No reviews available yet.</p>
        ) : (
          <div className="space-y-4">
            {reviewList.map((review) => (
              <Card key={review.id}>
                <CardHeader>
                  <CardTitle className="flex items-center justify-between flex-wrap gap-2">
                    <div className="flex items-center gap-3">
                      <span className="text-base">{review.author}</span>
                      <StarRating rating={review.rating} />
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge
                        className={sentimentBadgeClass(review.sentimentLabel)}
                        variant={sentimentBadgeVariant(review.sentimentLabel)}
                      >
                        {review.sentimentLabel}
                      </Badge>
                      <span className="text-xs text-muted-foreground">
                        {new Date(review.publishedAt).toLocaleDateString("fr-FR")}
                      </span>
                    </div>
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-foreground/80 leading-relaxed">{review.content}</p>
                </CardContent>
              </Card>
            ))}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-4 pt-4">
            <Button variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <span className="text-sm text-muted-foreground">
              Page {page + 1} of {totalPages}
            </span>
            <Button
              variant="outline"
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
