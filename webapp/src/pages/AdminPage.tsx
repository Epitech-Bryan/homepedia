import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Play, Trash2, Layers, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  fetchJobsStatus,
  triggerImport,
  IMPORT_JOBS,
  JobAlreadyRunningError,
  CACHES,
  evictCache,
  evictAllCaches,
  fetchPartitionStats,
  truncateDvfYear,
  refreshDvfYearStats,
  type JobsStatus,
  type PartitionYearCount,
} from "@/api/admin";
import { useAuth } from "@/auth/AuthContext";
import { useNavigate } from "react-router-dom";

const POLL_INTERVAL_MS = 4000;

// DVF datasets are published yearly; import targets a specific year and swaps
// just that partition. data.gouv.fr only serves geo-dvf for 2021+ on the
// `latest` path (older years 404). Latest is current year - 1 (year N
// publishes in early N+1). Build the list once at module load — it doesn't
// change per render and React's purity rules forbid Date.now()/new Date() in
// render.
const DVF_OLDEST_YEAR = 2021;
const DVF_LATEST_YEAR = new Date().getFullYear() - 1;
const DVF_YEARS: number[] = (() => {
  const out: number[] = [];
  for (let y = DVF_LATEST_YEAR; y >= DVF_OLDEST_YEAR; y--) out.push(y);
  return out;
})();

function formatCount(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(0)}k`;
  return String(n);
}

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

function formatDuration(ms: number | null | undefined): string | null {
  if (ms == null || ms < 0) return null;
  const sec = Math.round(ms / 1000);
  if (sec < 60) return `${sec}s`;
  const min = Math.floor(sec / 60);
  const remSec = sec % 60;
  if (min < 60) return remSec === 0 ? `${min}m` : `${min}m ${remSec}s`;
  const hours = Math.floor(min / 60);
  const remMin = min % 60;
  return remMin === 0 ? `${hours}h` : `${hours}h ${remMin}m`;
}

export function AdminPage() {
  const { user, loading } = useAuth();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [errors, setErrors] = useState<Record<string, string | null>>({});
  const [pendingTrigger, setPendingTrigger] = useState<string | null>(null);
  const [cacheErrors, setCacheErrors] = useState<Record<string, string | null>>({});
  const [pendingCacheEvict, setPendingCacheEvict] = useState<string | null>(null);
  const [cacheEvictedAt, setCacheEvictedAt] = useState<Record<string, number | null>>({});
  const [dvfYear, setDvfYear] = useState<number>(DVF_LATEST_YEAR);
  const [bulkProgress, setBulkProgress] = useState<{ year: number; total: number } | null>(null);
  const [pendingTruncate, setPendingTruncate] = useState<number | null>(null);
  const [pendingRefresh, setPendingRefresh] = useState<number | null>(null);

  useEffect(() => {
    if (!loading && !user) navigate("/", { replace: true });
  }, [loading, user, navigate]);

  const { data: status } = useQuery<JobsStatus>({
    queryKey: ["admin", "jobsStatus"],
    queryFn: fetchJobsStatus,
    refetchInterval: POLL_INTERVAL_MS,
    enabled: !!user,
  });

  const { data: partitionStats } = useQuery<PartitionYearCount[]>({
    queryKey: ["admin", "partitionStats"],
    queryFn: fetchPartitionStats,
    refetchInterval: POLL_INTERVAL_MS,
    enabled: !!user,
  });

  const anyRunning = useMemo(
    () => Object.values(status ?? {}).some((s) => s.state === "RUNNING"),
    [status],
  );

  const onTrigger = async (slug: string, paramsOverride?: Record<string, string | number>) => {
    setPendingTrigger(slug);
    setErrors((prev) => ({ ...prev, [slug]: null }));
    try {
      const params: Record<string, string | number> =
        paramsOverride ?? (slug === "dvf" ? { year: dvfYear } : {});
      await triggerImport(slug, params);
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

  const onTruncateYear = async (year: number) => {
    if (
      !window.confirm(
        `Vider toutes les données DVF de l'année ${year} ? Cette action est irréversible.`,
      )
    ) {
      return;
    }
    setPendingTruncate(year);
    setErrors((prev) => ({ ...prev, dvf: null }));
    try {
      await truncateDvfYear(year);
      qc.invalidateQueries({ queryKey: ["admin", "partitionStats"] });
    } catch (err) {
      const message =
        err instanceof JobAlreadyRunningError
          ? "Import DVF en cours, réessayez après."
          : err instanceof Error
            ? err.message
            : "Erreur inconnue";
      setErrors((prev) => ({ ...prev, dvf: message }));
    } finally {
      setPendingTruncate(null);
    }
  };

  const onRefreshYearStats = async (year: number) => {
    setPendingRefresh(year);
    setErrors((prev) => ({ ...prev, dvf: null }));
    try {
      await refreshDvfYearStats(year);
      qc.invalidateQueries({ queryKey: ["admin", "partitionStats"] });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Erreur inconnue";
      setErrors((prev) => ({ ...prev, dvf: message }));
    } finally {
      setPendingRefresh(null);
    }
  };

  const onBulkImportDvf = async () => {
    setErrors((prev) => ({ ...prev, dvf: null }));
    setBulkProgress({ year: DVF_YEARS[0], total: DVF_YEARS.length });
    try {
      for (const y of DVF_YEARS) {
        setBulkProgress({ year: y, total: DVF_YEARS.length });
        await triggerImport("dvf", { year: y });
        // Wait until the job finishes before launching the next year — server
        // returns 409 if we try to overlap two DVF runs.
        let stillRunning = true;
        while (stillRunning) {
          await new Promise((r) => setTimeout(r, 4000));
          const s = await fetchJobsStatus();
          stillRunning = s.dvf?.state === "RUNNING";
        }
        qc.invalidateQueries({ queryKey: ["admin", "jobsStatus"] });
        qc.invalidateQueries({ queryKey: ["admin", "partitionStats"] });
      }
    } catch (err) {
      const message =
        err instanceof JobAlreadyRunningError
          ? "Déjà en cours."
          : err instanceof Error
            ? err.message
            : "Erreur inconnue";
      setErrors((prev) => ({ ...prev, dvf: message }));
    } finally {
      setBulkProgress(null);
    }
  };

  const onEvictCache = async (name: string) => {
    setPendingCacheEvict(name);
    setCacheErrors((prev) => ({ ...prev, [name]: null }));
    try {
      if (name === "__all__") {
        await evictAllCaches();
        setCacheEvictedAt((prev) => {
          const now = Date.now();
          return {
            ...prev,
            ...Object.fromEntries(CACHES.map((c) => [c.name, now] as const)),
            __all__: now,
          };
        });
      } else {
        await evictCache(name);
        setCacheEvictedAt((prev) => ({ ...prev, [name]: Date.now() }));
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Erreur inconnue";
      setCacheErrors((prev) => ({ ...prev, [name]: message }));
    } finally {
      setPendingCacheEvict(null);
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
                    {running ? (
                      "en cours…"
                    ) : (
                      <>
                        {formatRelative(s?.lastRunAt ?? null)}
                        {formatDuration(s?.lastDurationMs) && (
                          <span className="ml-1.5 text-muted-foreground/60 tabular-nums">
                            ({formatDuration(s?.lastDurationMs)})
                          </span>
                        )}
                      </>
                    )}
                  </span>
                </CardTitle>
              </CardHeader>
              <CardContent className="p-3 pt-1 flex items-center justify-between gap-2">
                <p className="text-xs text-muted-foreground">{job.description}</p>
                <div className="flex items-center gap-2">
                  {job.slug === "dvf" && (
                    <select
                      className="h-8 rounded-md border bg-background px-2 text-xs"
                      value={dvfYear}
                      onChange={(e) => setDvfYear(Number(e.target.value))}
                      disabled={disabled}
                      aria-label="Année DVF"
                    >
                      {DVF_YEARS.map((y) => (
                        <option key={y} value={y}>
                          {y}
                        </option>
                      ))}
                    </select>
                  )}
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
                </div>
              </CardContent>
              {job.slug === "dvf" && (
                <div className="px-3 pb-3 flex flex-col gap-2">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-xs text-muted-foreground">
                      Importer toutes les années{" "}
                      <span className="font-medium">
                        {DVF_OLDEST_YEAR}–{DVF_LATEST_YEAR}
                      </span>{" "}
                      en séquence (chaque année swap sa partition à la fin).
                    </p>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={onBulkImportDvf}
                      disabled={disabled || bulkProgress !== null}
                    >
                      {bulkProgress ? (
                        <Loader2 className="h-3 w-3 animate-spin" />
                      ) : (
                        <Layers className="h-3 w-3" />
                      )}
                      {bulkProgress
                        ? `Année ${bulkProgress.year} (${bulkProgress.total - DVF_YEARS.indexOf(bulkProgress.year)}/${bulkProgress.total})`
                        : "Tout importer"}
                    </Button>
                  </div>
                  {partitionStats &&
                    partitionStats.filter((p) => DVF_YEARS.includes(p.year)).length > 0 && (
                      <div className="rounded border bg-muted/30 overflow-hidden">
                        <table className="w-full text-xs">
                          <thead className="bg-muted/50 text-muted-foreground">
                            <tr>
                              <th className="px-2 py-1 text-left font-medium">Année</th>
                              <th className="px-2 py-1 text-right font-medium">Lignes (approx.)</th>
                              <th className="px-2 py-1 w-px font-medium" aria-label="Action" />
                            </tr>
                          </thead>
                          <tbody>
                            {partitionStats
                              .filter((p) => DVF_YEARS.includes(p.year))
                              .map((p) => {
                                const rowDisabled = disabled || bulkProgress !== null;
                                const dur = formatDuration(p.lastDurationMs);
                                return (
                                  <tr key={p.year} className="border-t border-border/50">
                                    <td className="px-2 py-1 font-medium">
                                      {p.year}
                                      {dur && (
                                        <span
                                          className="ml-2 font-normal tabular-nums text-muted-foreground/60"
                                          title={
                                            p.lastRunAt
                                              ? `Dernier import réussi ${formatRelative(p.lastRunAt)}`
                                              : undefined
                                          }
                                        >
                                          {dur}
                                        </span>
                                      )}
                                    </td>
                                    <td
                                      className="px-2 py-1 text-right tabular-nums text-muted-foreground"
                                      title={`${p.approxCount.toLocaleString("fr-FR")} lignes (estimation planner)`}
                                    >
                                      {p.approxCount === 0 ? "—" : formatCount(p.approxCount)}
                                    </td>
                                    <td className="px-1 py-0.5 text-right whitespace-nowrap">
                                      <Button
                                        size="sm"
                                        variant="ghost"
                                        className="h-6 w-6 p-0"
                                        onClick={() => onTrigger("dvf", { year: p.year })}
                                        disabled={
                                          rowDisabled ||
                                          pendingTruncate !== null ||
                                          pendingRefresh !== null
                                        }
                                        aria-label={`Importer DVF ${p.year}`}
                                        title={`Importer DVF ${p.year}`}
                                      >
                                        <Play className="h-3 w-3" />
                                      </Button>
                                      <Button
                                        size="sm"
                                        variant="ghost"
                                        className="h-6 w-6 p-0"
                                        onClick={() => onRefreshYearStats(p.year)}
                                        disabled={
                                          rowDisabled ||
                                          pendingTruncate !== null ||
                                          pendingRefresh !== null ||
                                          p.approxCount === 0
                                        }
                                        aria-label={`Recalculer stats ${p.year}`}
                                        title={`Recalculer city_dvf_yearly_stats pour ${p.year}`}
                                      >
                                        {pendingRefresh === p.year ? (
                                          <Loader2 className="h-3 w-3 animate-spin" />
                                        ) : (
                                          <RefreshCw className="h-3 w-3" />
                                        )}
                                      </Button>
                                      <Button
                                        size="sm"
                                        variant="ghost"
                                        className="h-6 w-6 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
                                        onClick={() => onTruncateYear(p.year)}
                                        disabled={
                                          rowDisabled ||
                                          pendingTruncate !== null ||
                                          pendingRefresh !== null ||
                                          p.approxCount === 0
                                        }
                                        aria-label={`Vider DVF ${p.year}`}
                                        title={`Vider la partition DVF ${p.year}`}
                                      >
                                        {pendingTruncate === p.year ? (
                                          <Loader2 className="h-3 w-3 animate-spin" />
                                        ) : (
                                          <Trash2 className="h-3 w-3" />
                                        )}
                                      </Button>
                                    </td>
                                  </tr>
                                );
                              })}
                          </tbody>
                        </table>
                      </div>
                    )}
                </div>
              )}
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

      <header className="flex flex-col gap-1 mt-4">
        <h2 className="text-sm font-semibold">Cache Redis</h2>
        <p className="text-xs text-muted-foreground">
          Vide les entrées en cache pour forcer la régénération depuis la base au prochain accès.
        </p>
      </header>

      <div className="flex flex-col gap-2">
        {CACHES.map((cache) => {
          const evicting = pendingCacheEvict === cache.name;
          const allBusy = pendingCacheEvict === "__all__";
          const disabled = evicting || allBusy;
          const cacheError = cacheErrors[cache.name];
          const evictedAt = cacheEvictedAt[cache.name];
          return (
            <Card key={cache.name} className="border bg-background">
              <CardHeader className="p-3 pb-1">
                <CardTitle className="text-sm font-medium flex items-center justify-between">
                  <span>{cache.label}</span>
                  <span className="text-xs font-normal text-muted-foreground">
                    {evictedAt ? `vidé ${formatRelative(new Date(evictedAt).toISOString())}` : "—"}
                  </span>
                </CardTitle>
              </CardHeader>
              <CardContent className="p-3 pt-1 flex items-center justify-between gap-2">
                <p className="text-xs text-muted-foreground">{cache.description}</p>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => onEvictCache(cache.name)}
                  disabled={disabled}
                >
                  {disabled ? (
                    <Loader2 className="h-3 w-3 animate-spin" />
                  ) : (
                    <Trash2 className="h-3 w-3" />
                  )}
                  Vider
                </Button>
              </CardContent>
              {cacheError && (
                <div className="px-3 pb-3 text-xs">
                  <span className="text-destructive">{cacheError}</span>
                </div>
              )}
            </Card>
          );
        })}

        <Card className="border bg-background">
          <CardContent className="p-3 flex items-center justify-between gap-2">
            <p className="text-xs text-muted-foreground">
              Vider <span className="font-medium">tous</span> les caches Redis en une seule
              opération.
            </p>
            <Button
              size="sm"
              variant="destructive"
              onClick={() => onEvictCache("__all__")}
              disabled={pendingCacheEvict !== null}
            >
              {pendingCacheEvict === "__all__" ? (
                <Loader2 className="h-3 w-3 animate-spin" />
              ) : (
                <Trash2 className="h-3 w-3" />
              )}
              Tout vider
            </Button>
          </CardContent>
          {cacheErrors["__all__"] && (
            <div className="px-3 pb-3 text-xs">
              <span className="text-destructive">{cacheErrors["__all__"]}</span>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
