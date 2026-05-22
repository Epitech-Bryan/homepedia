# Observability manifests (issue #2)

Reference manifests for the homepedia rest-api production monitoring. The
actual cluster repo lives separately; copy these in or `kustomize` them
from there.

## Files

- **grafana-alerts.yaml** — `PrometheusRule` CR (kube-prometheus-stack).
  Five alerts: 5xx burst, p95 latency, cache miss burst (the 2026-05-21
  pattern), JVM heap pressure, HikariCP pool saturation.
- **smoke-cronjob.yaml** — `CronJob` that probes 4 representative
  endpoints every 5 min and fails on non-2xx / empty body / >2 s latency.
  Picked up by kube-state-metrics job alerts.

## Apply

```bash
kubectl apply -f grafana-alerts.yaml
kubectl apply -f smoke-cronjob.yaml
```

Both go in the `monitoring` namespace.

## Acceptance (from issue #2)

- [ ] Alert fires within 5 min when a deploy returns empty payloads
      (verify by deploying a deliberately broken build to staging).
- [ ] Smoke CronJob visible in `monitoring` namespace, success rate
      ≥ 99 % over a week.
