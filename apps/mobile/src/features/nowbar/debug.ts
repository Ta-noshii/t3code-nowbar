import type { NowBarRow } from "./model";

export const debugStates = [
  { id: "working", label: "Working", phase: "working", status: "Building the next feature" },
  { id: "progress", label: "Plan progress", phase: "working", status: "Implementing step three" },
  {
    id: "approval",
    label: "Approval",
    phase: "attention",
    kind: "approval",
    status: "Permission needed to continue",
  },
  {
    id: "input",
    label: "Needs input",
    phase: "attention",
    kind: "input",
    status: "Your agent has a question",
  },
  {
    id: "plan",
    label: "Plan ready",
    phase: "attention",
    kind: "plan",
    status: "Review the proposed plan",
  },
  {
    id: "background",
    label: "Background work",
    phase: "working",
    kind: "background",
    status: "Background task is running",
  },
  { id: "monitoring", label: "Watching", phase: "monitoring", status: "Watching for changes" },
  { id: "offline", label: "Disconnected", phase: "offline", status: "Connection paused" },
  {
    id: "completed",
    label: "Unread result",
    phase: "completed",
    status: "Work complete · Ready to review",
  },
  { id: "error", label: "Error", phase: "error", status: "Agent hit an error" },
  { id: "stopped", label: "Stopped", phase: "stopped", status: "Run was interrupted" },
] as const;

export type DebugState = (typeof debugStates)[number]["id"];
export function debugRow(id: DebugState, now: number, completed = 3): NowBarRow {
  const state = debugStates.find((state) => state.id === id)!;
  return {
    key: JSON.stringify(["nowbar-lab", id, "test-turn"]),
    title: state.label,
    project: "Now Bar Lab",
    provider: "codex",
    model: "gpt-6",
    eventAt: now,
    phase: state.phase,
    ...("kind" in state ? { kind: state.kind } : {}),
    status: state.status,
    startedAt: now - 7 * 60_000,
    total: id === "progress" ? 8 : 0,
    completed: id === "progress" ? Math.max(0, Math.min(8, completed)) : 0,
    url: "t3code-nowbar://",
  };
}
