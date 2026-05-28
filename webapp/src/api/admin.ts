const BASE_URL = "/api/admin";

export type JobState = "RUNNING" | "IDLE";

export type JobStatusView = {
  state: JobState;
  lastRunAt: string | null;
  lastStatus: string | null;
  lastBatchStatus: string;
  lastDurationMs: number | null;
  /**
   * Truncated ExitStatus.exitDescription from the last execution — usually
   * the failure stack trace's first lines. Null when the run completed
   * cleanly. Shown in the admin UI under the FAILED badge to surface root
   * cause without digging through pod logs.
   */
  lastExitMessage?: string | null;
  /**
   * Live phase label of the in-flight run (e.g. "Téléchargement ADEME…",
   * "Écriture des indicateurs…"). Best-effort, only present while the job is
   * RUNNING and the import service has pushed a phase. Null otherwise.
   */
  phase?: string | null;
};

export type JobsStatus = Record<string, JobStatusView>;

export type ImportJobDef = {
  slug: string;
  label: string;
  description: string;
};

export const IMPORT_JOBS: ImportJobDef[] = [
  { slug: "dvf", label: "DVF", description: "Transactions immobilières (data.gouv.fr)" },
  { slug: "insee", label: "INSEE", description: "Régions, départements, communes" },
  { slug: "geo", label: "GeoJSON", description: "Frontières régions et départements" },
  { slug: "dpe", label: "DPE", description: "Diagnostics de performance énergétique" },
  { slug: "health", label: "Santé", description: "Établissements de santé" },
  { slug: "reviews", label: "Reviews", description: "Avis Google Maps (MongoDB)" },
  { slug: "economy", label: "Économie", description: "Indicateurs économiques" },
  { slug: "education", label: "Éducation", description: "Indicateurs éducation" },
  { slug: "environment", label: "Environnement", description: "Indicateurs environnement" },
  { slug: "infrastructure", label: "Infrastructure", description: "Indicateurs infrastructure" },
];

export async function fetchJobsStatus(): Promise<JobsStatus> {
  const res = await fetch(`${BASE_URL}/jobs/status`, { credentials: "include" });
  if (!res.ok) {
    throw new Error(`Failed to fetch jobs status: ${res.status}`);
  }
  return res.json();
}

export async function triggerImport(
  slug: string,
  params: Record<string, string | number> = {},
): Promise<void> {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    search.set(k, String(v));
  }
  const qs = search.toString();
  const url = `${BASE_URL}/imports/${slug}${qs ? `?${qs}` : ""}`;
  const res = await fetch(url, {
    method: "POST",
    credentials: "include",
  });
  if (res.status === 409) {
    throw new JobAlreadyRunningError(slug);
  }
  if (!res.ok) {
    throw new Error(`Failed to trigger ${slug}: ${res.status} ${res.statusText}`);
  }
}

export class JobAlreadyRunningError extends Error {
  constructor(public readonly slug: string) {
    super(`Job '${slug}' is already running`);
    this.name = "JobAlreadyRunningError";
  }
}

export type CacheDef = {
  name: string;
  label: string;
  description: string;
};

export const CACHES: CacheDef[] = [
  { name: "geo", label: "GeoJSON", description: "Polygones régions/départements (TTL 24h)" },
  {
    name: "refdata",
    label: "Référentiels",
    description: "Régions, départements, communes (TTL 12h)",
  },
  { name: "stats", label: "Statistiques", description: "Agrégats DVF/DPE (TTL 30 min)" },
  { name: "reviews", label: "Avis", description: "Word clouds et sentiments (TTL 15 min)" },
  { name: "pois", label: "POIs OSM", description: "Proxy Overpass musées/gares/etc. (TTL 7 j)" },
];

export async function evictCache(name: string): Promise<void> {
  const res = await fetch(`${BASE_URL}/caches/${name}/evict`, {
    method: "POST",
    credentials: "include",
  });
  if (!res.ok) {
    throw new Error(`Failed to evict cache '${name}': ${res.status} ${res.statusText}`);
  }
}

export async function evictAllCaches(): Promise<void> {
  const res = await fetch(`${BASE_URL}/evict-all-caches`, {
    method: "POST",
    credentials: "include",
  });
  if (!res.ok) {
    throw new Error(`Failed to evict all caches: ${res.status} ${res.statusText}`);
  }
}

export type PartitionYearCount = {
  year: number;
  approxCount: number;
  lastRunAt: string | null;
  lastDurationMs: number | null;
};

export async function fetchPartitionStats(): Promise<PartitionYearCount[]> {
  const res = await fetch(`${BASE_URL}/transactions/partition-stats`, {
    credentials: "include",
  });
  if (!res.ok) {
    throw new Error(`Failed to fetch partition stats: ${res.status}`);
  }
  return res.json();
}

export async function truncateDvfYear(year: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/transactions/${year}`, {
    method: "DELETE",
    credentials: "include",
  });
  if (res.status === 409) {
    throw new JobAlreadyRunningError("dvf");
  }
  if (!res.ok) {
    throw new Error(`Failed to truncate year ${year}: ${res.status} ${res.statusText}`);
  }
}

export async function refreshDvfYearStats(year: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/transactions/${year}/refresh-stats`, {
    method: "POST",
    credentials: "include",
  });
  if (!res.ok) {
    throw new Error(`Failed to refresh stats for ${year}: ${res.status} ${res.statusText}`);
  }
}

/**
 * Triggers an async rebuild of the commune vector tiles (cities.mbtiles).
 * Returns immediately — actual tippecanoe pipeline takes ~20 min in the pod.
 * Throws {@link JobAlreadyRunningError} on 409 (a build is already in flight),
 * generic error on other failure codes.
 */
export async function triggerTilesRebuild(): Promise<void> {
  const res = await fetch(`${BASE_URL}/tiles/rebuild`, {
    method: "POST",
    credentials: "include",
  });
  if (res.status === 409) {
    throw new JobAlreadyRunningError("tiles");
  }
  if (!res.ok) {
    throw new Error(`Failed to trigger tiles rebuild: ${res.status} ${res.statusText}`);
  }
}

/**
 * Triggers an async rebuild of the world vector tiles (world.mbtiles —
 * countries + admin-1 + admin-2 for ~110 / 66 countries respectively).
 * Faster than the cities rebuild (~2-3 min) since the source files are
 * already on the PVC.
 */
export async function triggerWorldTilesRebuild(): Promise<void> {
  const res = await fetch(`${BASE_URL}/tiles/world/rebuild`, {
    method: "POST",
    credentials: "include",
  });
  if (res.status === 409) {
    throw new JobAlreadyRunningError("world-tiles");
  }
  if (!res.ok) {
    throw new Error(`Failed to trigger world tiles rebuild: ${res.status} ${res.statusText}`);
  }
}
