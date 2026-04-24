import { Skeleton } from '@/components/ui/skeleton';

export function LoadingSpinner() {
  return (
    <div className="space-y-4 py-8">
      <Skeleton className="h-8 w-64" />
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Skeleton className="h-24" />
        <Skeleton className="h-24" />
        <Skeleton className="h-24" />
      </div>
      <Skeleton className="h-[400px] w-full" />
    </div>
  );
}
