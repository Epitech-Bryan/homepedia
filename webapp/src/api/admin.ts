const BASE_URL = "/api/admin";

export type JobState = "RUNNING" | "IDLE";

export type JobStatusView = {
  state: JobState;
  lastRunAt: string | null;
  lastStatus: string | null;
  lastBatchStatus: string;
  lastDurationMs: number | null;
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
