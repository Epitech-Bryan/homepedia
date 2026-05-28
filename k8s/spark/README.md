# Spark manifests

Production wiring for the heavy DVF aggregation. Spark runs as a one-shot
`spark-submit --master local[*]` inside a single pod (no standalone cluster):
the job reads the `transactions` table over JDBC, aggregates per department,
and overwrites `dept_dvf_stats`. The same image and command run locally via
the `spark` compose profile, so local mirrors prod.

The job is a shaded jar (`com.homepedia.spark.DvfAggregateJob`) baked into the
`apache/spark:3.5.8-scala2.12-java17-ubuntu` image. Scala 2.12 is mandatory —
the official `apache/spark` 3.5.x images only ship 2.12 variants, and a 2.13
build fails class loading at runtime. The PostgreSQL JDBC driver is bundled in
the shaded jar, so no `--jars` is needed.

## Files

- **cronjob.yaml** — `CronJob spark-dvf-aggregate` (daily 04:00). Pulls DB
  credentials from the `homepedia-db` Secret (keys `jdbc-url`, `username`,
  `password`).
- **rbac.yaml** — `ServiceAccount homepedia-spark-launcher` + `Role` /
  `RoleBinding` granting create/watch on Jobs so the rest-api pod can launch
  an on-demand run from the CronJob template.

## Apply

```bash
kubectl apply -f rbac.yaml
kubectl apply -f cronjob.yaml
```

Both go in the `homepedia` namespace.

## On-demand launch from the admin endpoint

`SparkJobLauncherService` shells out (the image carries `kubectl`) to:

```bash
N=spark-dvf-aggregate-manual-$(date +%s)
kubectl -n homepedia create job --from=cronjob/spark-dvf-aggregate "$N"
kubectl -n homepedia wait --for=condition=complete --timeout=1700s "job/$N"
```

This blocks until the Job completes so the stats cache is only evicted once
the fresh `dept_dvf_stats` are committed. For it to work the rest-api
Deployment must set `serviceAccountName: homepedia-spark-launcher`. Override
the whole command with `HOMEPEDIA_SPARK_SUBMIT_COMMAND` if needed.

## Prerequisites to verify in the target cluster

- `homepedia-db` Secret exists with `jdbc-url` / `username` / `password`
  (or edit the `secretKeyRef`s to match the existing datasource Secret).
- The image `ghcr.io/epitech-bryan/homepedia-spark-jobs` is built and pushed
  (multi-stage `backend/spark-jobs/Dockerfile`, build context `backend/`).
- The rest-api Deployment runs under `homepedia-spark-launcher`.
