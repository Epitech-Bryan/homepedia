import { memo } from "react";
import { Handle, Position, type NodeProps } from "@xyflow/react";

/**
 * Shared node renderer used by the architecture / DB / devops diagrams. Each
 * node is a small card with a title, optional subtitle and an accent stripe
 * so the visual hierarchy reads like a system map rather than an org chart.
 *
 * <p>
 * Accent colours are scoped to broad categories:
 * <ul>
 *   <li>{@code edge} (sky) — entry points (browser, GitHub, registry)</li>
 *   <li>{@code service} (emerald) — app workloads (webapp, REST API)</li>
 *   <li>{@code data} (amber) — stateful stores (Postgres, Mongo, Redis)</li>
 *   <li>{@code infra} (violet) — cluster plumbing (Traefik, Flux, Vault)</li>
 *   <li>{@code job} (rose) — batch / cron workloads (Spark, tile builder)</li>
 * </ul>
 */
export type NodeKind = "edge" | "service" | "data" | "infra" | "job";

const ACCENT: Record<NodeKind, string> = {
  edge: "bg-sky-500",
  service: "bg-emerald-500",
  data: "bg-amber-500",
  infra: "bg-violet-500",
  job: "bg-rose-500",
};

interface SchemaNodeData {
  kind: NodeKind;
  title: string;
  subtitle?: string;
  hint?: string;
  ports?: { top?: boolean; bottom?: boolean; left?: boolean; right?: boolean };
  [key: string]: unknown;
}

export const SchemaNode = memo(function SchemaNode({ data }: NodeProps) {
  const d = data as unknown as SchemaNodeData;
  const ports = d.ports ?? { top: true, bottom: true };
  return (
    <div className="min-w-[180px] rounded-md border border-border bg-card shadow-sm">
      <div className={`h-1 w-full rounded-t-md ${ACCENT[d.kind]}`} />
      <div className="px-3 py-2">
        <div className="text-xs font-semibold leading-tight">{d.title}</div>
        {d.subtitle && (
          <div className="mt-0.5 font-mono text-[10px] uppercase tracking-wide text-muted-foreground">
            {d.subtitle}
          </div>
        )}
        {d.hint && (
          <div className="mt-1 text-[10px] leading-snug text-muted-foreground">{d.hint}</div>
        )}
      </div>
      {ports.top && (
        <Handle
          type="target"
          position={Position.Top}
          className="!h-1.5 !w-1.5 !bg-muted-foreground"
        />
      )}
      {ports.bottom && (
        <Handle
          type="source"
          position={Position.Bottom}
          className="!h-1.5 !w-1.5 !bg-muted-foreground"
        />
      )}
      {ports.left && (
        <Handle
          type="target"
          position={Position.Left}
          id="left"
          className="!h-1.5 !w-1.5 !bg-muted-foreground"
        />
      )}
      {ports.right && (
        <Handle
          type="source"
          position={Position.Right}
          id="right"
          className="!h-1.5 !w-1.5 !bg-muted-foreground"
        />
      )}
    </div>
  );
});

/**
 * Static group/cluster background — used to visually delimit a Kubernetes
 * namespace or a CI/CD stage. Rendered as a non-interactive node so the
 * built-in ReactFlow rendering keeps it behind the regular ones via z-order.
 */
export const GroupNode = memo(function GroupNode({ data }: NodeProps) {
  const d = data as unknown as { label: string; width: number; height: number; tint?: string };
  const tint = d.tint ?? "bg-muted/30 border-border";
  return (
    <div
      className={`rounded-lg border-2 border-dashed ${tint}`}
      style={{ width: d.width, height: d.height }}
    >
      <div className="px-3 py-1.5 text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
        {d.label}
      </div>
    </div>
  );
});

/* eslint-disable react-refresh/only-export-components -- nodeTypes must
   live next to the components it references; React Flow only accepts
   objects keyed by type name. Splitting them off would re-introduce the
   import cycle the diagram-nodes module was designed to solve. */
export const nodeTypes = {
  schema: SchemaNode,
  group: GroupNode,
};
