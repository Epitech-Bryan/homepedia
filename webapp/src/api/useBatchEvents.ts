import { useEffect, useState } from "react";

export interface BatchEvent {
  type: "STARTING" | "RUNNING" | "COMPLETED" | "FAILED";
  job: string;
  message: string;
  at: string;
}

/**
 * Subscribes to /api/events/batch (Server-Sent Events) and exposes the rolling
 * window of recent batch events. Auto-reconnects on browser-driven retries
 * (default EventSource behavior).
 */
export function useBatchEvents(maxEvents = 20): BatchEvent[] {
  const [events, setEvents] = useState<BatchEvent[]>([]);

  useEffect(() => {
    const source = new EventSource("/api/events/batch");

    const onBatch = (e: MessageEvent) => {
      try {
        const parsed = JSON.parse(e.data) as BatchEvent;
        setEvents((prev) => [parsed, ...prev].slice(0, maxEvents));
      } catch {
        // ignore malformed payloads
      }
    };

    source.addEventListener("batch", onBatch as EventListener);
    return () => {
      source.removeEventListener("batch", onBatch as EventListener);
      source.close();
    };
  }, [maxEvents]);

  return events;
}
