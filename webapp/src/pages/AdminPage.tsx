import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Play } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  fetchJobsStatus,
  triggerImport,
  IMPORT_JOBS,
  JobAlreadyRunningError,
  type JobsStatus,
} from "@/api/admin";
import { useAuth } from "@/auth/AuthContext";
import { useNavigate } from "react-router-dom";

const POLL_INTERVAL_MS = 4000;

function formatRelative(iso: string | null): string {
  if (!iso) return "jamais lancé";
  const diff = Date.now() - new Date(iso).getTime();
  const sec = Math.floor(diff / 1000);
  if (sec < 60) return "il y a quelques secondes";
  const min = Math.floor(sec / 60);
  if (min < 60) return `il y a ${min} min`;
  const hours = Math.floor(min / 60);
  if (hours < 24) return `il y a ${hours} h`;
  const days = Math.floor(hours / 24);
  return `il y a ${days} j`;
}

export function AdminPage() {
  const { user, loading } = useAuth();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [errors, setErrors] = useState<Record<string, string | null>>({});
  const [pendingTrigger, setPendingTrigger] = useState<string | null>(null);

  useEffect(() => {
    if (!loading && !user) navigate("/", { replace: true });
  }, [loading, user, navigate]);

  const { data: status } = useQuery<JobsStatus>({
    queryKey: ["admin", "jobsStatus"],
    queryFn: fetchJobsStatus,
    refetchInterval: POLL_INTERVAL_MS,
    enabled: !!user,
  });

  const anyRunning = useMemo(
    () => Object.values(status ?? {}).some((s) => s.state === "RUNNING"),
    [status],
  );

  const onTrigger = async (slug: string) => {
    setPendingTrigger(slug);
    setErrors((prev) => ({ ...prev, [slug]: null }));
    try {
      await triggerImport(slug);
      qc.invalidateQueries({ queryKey: ["admin", "jobsStatus"] });
    } catch (err) {
      const message =
        err instanceof JobAlreadyRunningError
          ? "Déjà en cours."
          : err instanceof Error
            ? err.message
            : "Erreur inconnue";
      setErrors((prev) => ({ ...prev, [slug]: message }));
    } finally {
      setPendingTrigger(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center p-8 text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin mr-2" />
        Chargement…
      </div>
    );
  }

  if (!user) return null;

  return (
    <div className="flex flex-col gap-3">
      <header className="flex flex-col gap-1">
        <h1 className="text-base font-semibold">Console d'administration</h1>
        <p className="text-xs text-muted-foreground">
          Connecté en tant que <span className="font-medium">{user.username}</span>. Lance les jobs
          d'import à la demande. Les jobs déjà en cours sont désactivés.
          {anyRunning && (
            <span className="ml-1 inline-flex items-center gap-1 text-amber-600">
              <Loader2 className="h-3 w-3 animate-spin" /> import en cours…
            </span>
          )}
        </p>
      </header>

      <div className="flex flex-col gap-2">
        {IMPORT_JOBS.map((job) => {
          const s = status?.[job.slug];
          const running = s?.state === "RUNNING";
          const triggering = pendingTrigger === job.slug;
          const disabled = running || triggering;
          const error = errors[job.slug];
          return (
            <Card key={job.slug} className="border bg-background">
              <CardHeader className="p-3 pb-1">
                <CardTitle className="text-sm font-medium flex items-center justify-between">
                  <span>{job.label}</span>
                  <span className="text-xs font-normal text-muted-foreground">
                    {running ? "en cours…" : formatRelative(s?.lastRunAt ?? null)}
                  </span>
                </CardTitle>
              </CardHeader>
              <CardContent className="p-3 pt-1 flex items-center justify-between gap-2">
                <p className="text-xs text-muted-foreground">{job.description}</p>
                <Button
                  size="sm"
                  variant={running ? "secondary" : "default"}
                  onClick={() => onTrigger(job.slug)}
                  disabled={disabled}
                >
                  {disabled ? (
                    <Loader2 className="h-3 w-3 animate-spin" />
                  ) : (
                    <Play className="h-3 w-3" />
                  )}
                  {running ? "En cours" : "Lancer"}
                </Button>
              </CardContent>
              {(error || (s?.lastStatus && s.lastStatus !== "COMPLETED" && !running)) && (
                <div className="px-3 pb-3 text-xs">
                  {error && <span className="text-destructive">{error}</span>}
                  {!error && s?.lastStatus && s.lastStatus !== "COMPLETED" && !running && (
                    <span className="text-amber-600">Dernier run : {s.lastStatus}</span>
                  )}
                </div>
              )}
            </Card>
          );
        })}
      </div>
    </div>
  );
}
