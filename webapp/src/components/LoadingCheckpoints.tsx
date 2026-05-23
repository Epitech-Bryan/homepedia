/**
 * Progress strip shown above a section that awaits several parallel queries.
 * One row per checkpoint, marked as either pending or done. Auto-hides once
 * every checkpoint is done so it doesn't add noise after the data lands.
 *
 * <p>
 * The visual is intentionally text-only (no emoji per design rule): a small
 * filled or hollow dot serves as the state marker. Honest cadence — the row
 * flips the moment its query resolves, no artificial smoothing.
 */
import { cn } from "@/lib/utils";

export interface Checkpoint {
  label: string;
  done: boolean;
}

interface LoadingCheckpointsProps {
  checkpoints: Checkpoint[];
  /** Optional className for the container, e.g. for spacing in a page layout. */
  className?: string;
}

export function LoadingCheckpoints({ checkpoints, className }: LoadingCheckpointsProps) {
  const allDone = checkpoints.every((c) => c.done);
  if (allDone) return null;
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "rounded-lg border bg-card/60 px-4 py-3 text-sm shadow-sm",
        "transition-opacity duration-200",
        className,
      )}
    >
      <p className="text-xs font-medium text-muted-foreground mb-2">Chargement…</p>
      <ul className="space-y-1.5">
        {checkpoints.map((c) => (
          <li key={c.label} className="flex items-center gap-2 text-xs">
            <span
              aria-hidden
              className={cn(
                "inline-block h-2 w-2 rounded-full transition-colors",
                c.done
                  ? "bg-emerald-500"
                  : "border border-muted-foreground/40 bg-transparent animate-pulse",
              )}
            />
            <span className={c.done ? "text-foreground/60" : "text-foreground"}>{c.label}</span>
            <span className="ml-auto text-[10px] uppercase tracking-wider text-muted-foreground">
              {c.done ? "ok" : "…"}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
