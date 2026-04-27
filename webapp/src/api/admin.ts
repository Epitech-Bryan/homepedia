const BASE_URL = "/api/admin";

export type JobState = "RUNNING" | "IDLE";

export type JobStatusView = {
  state: JobState;
  lastRunAt: string | null;
  lastStatus: string | null;
  lastBatchStatus: string;
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

export async function triggerImport(slug: string): Promise<void> {
  const res = await fetch(`${BASE_URL}/imports/${slug}`, {
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
