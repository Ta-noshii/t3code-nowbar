import type { OrchestrationMessage, OrchestrationThreadStreamItem } from "@t3tools/contracts";
import * as Stream from "effect/Stream";
import type { EnvironmentProject, EnvironmentThreadShell } from "./state/shell.ts";

export interface NowBarRow {
  readonly key: string;
  readonly title: string;
  readonly project: string;
  readonly phase:
    | "working"
    | "attention"
    | "monitoring"
    | "offline"
    | "completed"
    | "error"
    | "stopped";
  readonly kind?: "approval" | "input" | "plan" | "background" | undefined;
  readonly status: string;
  readonly startedAt: number;
  readonly completed: number;
  readonly total: number;
  readonly url: string;
  readonly provider?: string;
  readonly model?: string;
  readonly modelLabel?: string;
  readonly eventAt?: number;
}

// The same provider catalog supplies names to the mobile model picker and host push.
export interface NowBarCatalog {
  readonly providers: ReadonlyArray<{
    readonly instanceId: string;
    readonly driver: string;
    readonly models: ReadonlyArray<{
      readonly slug: string;
      readonly name: string;
      readonly aliases?: ReadonlyArray<string> | undefined;
      readonly isDefault?: boolean | undefined;
    }>;
  }>;
}

export function threadKey(thread: EnvironmentThreadShell): string {
  return JSON.stringify([
    thread.environmentId,
    thread.id,
    thread.latestTurn?.turnId ?? "background",
  ]);
}

export function isActiveThread(thread: EnvironmentThreadShell): boolean {
  return (
    thread.archivedAt === null &&
    (thread.latestTurn?.state === "running" ||
      thread.hasPendingApprovals ||
      thread.hasPendingUserInput ||
      thread.hasActionableProposedPlan ||
      !!thread.backgroundLiveness)
  );
}

export function projectNowBarRows(
  threads: ReadonlyArray<EnvironmentThreadShell>,
  projects: ReadonlyArray<EnvironmentProject>,
  connected: ReadonlySet<string>,
  unread?: { readonly since: number; readonly readTurns: Readonly<Record<string, string>> },
  catalogs?: ReadonlyMap<string, NowBarCatalog>,
  statuses?: ReadonlyMap<string, string>,
): NowBarRow[] {
  const projectsByKey = new Map(
    projects.map((p) => [JSON.stringify([p.environmentId, p.id]), p.title]),
  );
  return threads
    .filter((thread) => isActiveThread(thread) || isUnreadCompletion(thread, unread))
    .map((thread): NowBarRow => {
      const online = connected.has(thread.environmentId);
      const finished = !isActiveThread(thread) && isUnreadCompletion(thread, unread);
      const attention =
        thread.hasPendingApprovals ||
        thread.hasPendingUserInput ||
        thread.hasActionableProposedPlan;
      const phase = finished
        ? thread.latestTurn?.state === "error"
          ? "error"
          : thread.latestTurn?.state === "interrupted"
            ? "stopped"
            : "completed"
        : !online
          ? "offline"
          : attention
            ? "attention"
            : thread.backgroundLiveness === "monitoring"
              ? "monitoring"
              : "working";
      const progress = thread.planProgress;
      const total = Math.max(0, progress?.totalSteps ?? 0);
      const status = finished
        ? phase === "error"
          ? "Agent hit an error · Open to inspect"
          : phase === "stopped"
            ? "Agent stopped · Open to review"
            : "Ready to review · Unread result"
        : !online
          ? "Connection paused · Open T3 to reconnect"
          : thread.hasPendingApprovals
            ? "Approval needed · Review to continue"
            : thread.hasPendingUserInput
              ? "Your agent has a question"
              : thread.hasActionableProposedPlan
                ? "Plan ready for your review"
                : ((phase === "working" && thread.latestTurn?.state === "running"
                    ? statuses?.get(threadKey(thread))
                    : undefined) ??
                  progress?.step ??
                  (phase === "monitoring" ? "Watching for changes" : "Agent is working"));
      const startedAt = Date.parse(
        thread.latestTurn?.startedAt ?? thread.latestTurn?.requestedAt ?? thread.updatedAt,
      );
      const provider = catalogs
        ?.get(thread.environmentId)
        ?.providers.find((provider) => provider.instanceId === thread.modelSelection.instanceId);
      const model =
        provider?.models.find((model) => model.slug === thread.modelSelection.model) ??
        provider?.models.find((model) => model.aliases?.includes(thread.modelSelection.model));
      return {
        key: threadKey(thread),
        provider:
          provider?.driver ??
          thread.session?.providerName ??
          String(thread.modelSelection.instanceId),
        model: thread.modelSelection.model,
        modelLabel: model?.name ?? thread.modelSelection.model,
        // Keep streaming message updates from changing an otherwise identical push payload.
        eventAt:
          Date.parse(
            thread.latestTurn?.completedAt ?? thread.latestTurn?.requestedAt ?? thread.createdAt,
          ) || 0,
        title: thread.title.slice(0, 120),
        project:
          projectsByKey.get(JSON.stringify([thread.environmentId, thread.projectId])) ?? "T3 Code",
        phase,
        kind: thread.hasPendingApprovals
          ? "approval"
          : thread.hasPendingUserInput
            ? "input"
            : thread.hasActionableProposedPlan
              ? "plan"
              : thread.backgroundLiveness === "working"
                ? "background"
                : undefined,
        status: status.slice(0, 240),
        startedAt: Number.isFinite(startedAt) ? startedAt : 0,
        total,
        completed: Math.max(0, Math.min(total, progress?.completedSteps ?? 0)),
        url: `t3code-nowbar://threads/${encodeURIComponent(thread.environmentId)}/${encodeURIComponent(thread.id)}`,
      };
    })
    .sort((a, b) => {
      const priority = {
        attention: 0,
        working: 2,
        monitoring: 3,
        offline: 4,
        completed: 1,
        error: 1,
        stopped: 1,
      };
      return (
        priority[a.phase] - priority[b.phase] ||
        b.startedAt - a.startedAt ||
        a.key.localeCompare(b.key)
      );
    });
}

export function readIdentity(thread: EnvironmentThreadShell): string {
  return JSON.stringify([thread.environmentId, thread.id]);
}

export function isUnreadCompletion(
  thread: EnvironmentThreadShell,
  unread?: { readonly since: number; readonly readTurns: Readonly<Record<string, string>> },
): boolean {
  const turn = thread.latestTurn;
  if (
    !unread ||
    thread.archivedAt !== null ||
    !turn ||
    (turn.state !== "completed" && turn.state !== "error" && turn.state !== "interrupted")
  )
    return false;
  const completedAt = Date.parse(turn.completedAt ?? "");
  return (
    Number.isFinite(completedAt) &&
    completedAt >= unread.since &&
    unread.readTurns[readIdentity(thread)] !== turn.turnId
  );
}

/** Only settle work observed live on this device; cached historical work never alerts. */
export function completedNowBarRows(
  previous: ReadonlyArray<NowBarRow>,
  threads: ReadonlyArray<EnvironmentThreadShell>,
  connected: ReadonlySet<string>,
): NowBarRow[] {
  const byKey = new Map(threads.map((thread) => [threadKey(thread), thread]));
  return previous.flatMap((row) => {
    if (row.phase === "completed" || row.phase === "error" || row.phase === "stopped") return [];
    const thread = byKey.get(row.key);
    if (
      !thread ||
      !connected.has(thread.environmentId) ||
      isActiveThread(thread) ||
      !thread.latestTurn
    )
      return [];
    const state = thread.latestTurn.state;
    if (state === "running") return [];
    return [
      {
        ...row,
        phase:
          state === "error"
            ? ("error" as const)
            : state === "interrupted"
              ? ("stopped" as const)
              : ("completed" as const),
        status:
          state === "error"
            ? "Agent hit an error · Open to inspect"
            : state === "interrupted"
              ? "Agent stopped"
              : "Work complete · Ready to review",
      },
    ];
  });
}

interface AgentStatusState {
  readonly text: string;
  readonly createdAt: string;
  readonly pendingId: string;
  readonly pendingText: string;
  readonly pendingCreatedAt: string;
}
export const emptyAgentStatus: AgentStatusState = {
  text: "",
  createdAt: "",
  pendingId: "",
  pendingText: "",
  pendingCreatedAt: "",
};

/** Retain a bounded preview while streaming, publish only complete agent updates. */
export function updateAgentStatus(
  state: AgentStatusState,
  message: Pick<
    OrchestrationMessage,
    "id" | "role" | "turnId" | "text" | "streaming" | "createdAt"
  >,
  turnId: string,
): AgentStatusState {
  if (
    message.role !== "assistant" ||
    message.turnId !== turnId ||
    message.createdAt < state.createdAt
  )
    return state;
  const pending = message.id === state.pendingId ? state.pendingText : "";
  if (message.streaming)
    return message.createdAt < state.pendingCreatedAt
      ? state
      : {
          ...state,
          pendingId: message.id,
          pendingCreatedAt: message.createdAt,
          pendingText: (pending + message.text).slice(0, 600),
        };
  const text = (message.text || pending).replace(/\s+/g, " ").trim().slice(0, 240);
  return text
    ? {
        ...state,
        text,
        createdAt: message.createdAt,
        ...(message.id === state.pendingId
          ? { pendingId: "", pendingText: "", pendingCreatedAt: "" }
          : {}),
      }
    : state;
}

export function agentStatusUpdates<E, R>(
  source: Stream.Stream<OrchestrationThreadStreamItem, E, R>,
  turnId: string,
) {
  return source.pipe(
    Stream.scan(emptyAgentStatus, (state, item) => {
      if (item.kind === "snapshot") {
        return item.snapshot.thread.messages.reduce((current, message) => {
          // Snapshot text is already accumulated; later deltas can continue its pending preview.
          return updateAgentStatus(current, message, turnId);
        }, emptyAgentStatus);
      }
      if (item.kind === "event" && item.event.type === "thread.message-sent") {
        const message = item.event.payload;
        return updateAgentStatus(state, { ...message, id: message.messageId }, turnId);
      }
      return state;
    }),
    Stream.map((state) => state.text),
    Stream.changes,
  );
}
