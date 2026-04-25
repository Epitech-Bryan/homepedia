import { useBatchEvents } from "@/api/useBatchEvents";
import { Badge } from "@/components/ui/badge";

const TYPE_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  STARTING: "secondary",
  RUNNING: "default",
  COMPLETED: "outline",
  FAILED: "destructive",
};

export function BatchEventsBanner() {
  const events = useBatchEvents(5);

  if (events.length === 0) return null;
  const latest = events[0];

  return (
    <div className="flex items-center justify-between gap-4 rounded-md border bg-muted/30 px-3 py-2 text-xs text-muted-foreground">
      <div className="flex items-center gap-2">
        <span
          className="inline-block h-2 w-2 rounded-full bg-emerald-500"
          aria-label="Live indicator"
        />
        <span>Live batch updates</span>
      </div>
      <div className="flex items-center gap-2">
        <Badge variant={TYPE_VARIANT[latest.type] ?? "default"}>{latest.type}</Badge>
        <span className="font-medium text-foreground">{latest.job}</span>
        <span>— {latest.message}</span>
        <span className="hidden sm:inline">
          ({new Date(latest.at).toLocaleTimeString("fr-FR")})
        </span>
      </div>
    </div>
  );
}
