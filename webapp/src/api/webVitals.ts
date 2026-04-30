import { onCLS, onINP, onLCP, onTTFB, onFCP, type Metric } from "web-vitals";

/**
 * Real-User Monitoring: report Core Web Vitals (LCP, INP, CLS, TTFB, FCP) to
 * the backend's /api/rum endpoint via navigator.sendBeacon (survives page
 * unload). Backend can stream the JSON to Loki / Prometheus / wherever.
 *
 * <p>
 * Cheap and privacy-friendly: no third-party SaaS. Sampled at 100% for now;
 * drop to e.g. 10% via Math.random() < 0.1 once we have enough volume.
 */
const ENDPOINT = "/api/rum";

function send(metric: Metric) {
  const body = JSON.stringify({
    name: metric.name,
    value: metric.value,
    rating: metric.rating,
    delta: metric.delta,
    id: metric.id,
    navigationType: metric.navigationType,
    path: window.location.pathname,
    ts: Date.now(),
  });

  // sendBeacon is fire-and-forget and queues even after the user navigates
  // away. Falls back to fetch with keepalive on browsers that miss it.
  if (navigator.sendBeacon) {
    navigator.sendBeacon(ENDPOINT, new Blob([body], { type: "application/json" }));
  } else {
    fetch(ENDPOINT, {
      method: "POST",
      body,
      keepalive: true,
      headers: { "Content-Type": "application/json" },
    }).catch(() => {
      /* swallow — RUM must never break the page */
    });
  }
}

export function reportWebVitals() {
  try {
    onCLS(send);
    onINP(send);
    onLCP(send);
    onTTFB(send);
    onFCP(send);
  } catch (err) {
    console.warn("web-vitals init failed", err);
  }
}
