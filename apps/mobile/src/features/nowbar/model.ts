import type {
  EnvironmentProject,
  EnvironmentThreadShell,
} from "@t3tools/client-runtime/state/shell";

export interface NowBarRow {
  readonly key: string;
  readonly title: string;
  readonly project: string;
  readonly phase: "working" | "attention" | "monitoring" | "offline" | "completed" | "error";
  readonly status: string;
  readonly startedAt: number;
  readonly completed: number;
  readonly total: number;
  readonly url: string;
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
): NowBarRow[] {
  const projectsByKey = new Map(
    projects.map((p) => [JSON.stringify([p.environmentId, p.id]), p.title]),
  );
  return threads
    .filter(isActiveThread)
    .map((thread): NowBarRow => {
      const online = connected.has(thread.environmentId);
      const attention =
        thread.hasPendingApprovals ||
        thread.hasPendingUserInput ||
        thread.hasActionableProposedPlan;
      const phase = !online
        ? "offline"
        : attention
          ? "attention"
          : thread.backgroundLiveness === "monitoring"
            ? "monitoring"
            : "working";
      const progress = thread.planProgress;
      const total = Math.max(0, progress?.totalSteps ?? 0);
      const status = !online
        ? "Connection paused · Open T3 to reconnect"
        : thread.hasPendingApprovals
          ? "Approval needed · Review to continue"
          : thread.hasPendingUserInput
            ? "Your agent has a question"
            : thread.hasActionableProposedPlan
              ? "Plan ready for your review"
              : (progress?.step ??
                (phase === "monitoring" ? "Watching for changes" : "Agent is working"));
      const startedAt = Date.parse(
        thread.latestTurn?.startedAt ?? thread.latestTurn?.requestedAt ?? thread.updatedAt,
      );
      return {
        key: threadKey(thread),
        title: thread.title.slice(0, 120),
        project:
          projectsByKey.get(JSON.stringify([thread.environmentId, thread.projectId])) ?? "T3 Code",
        phase,
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
        working: 1,
        monitoring: 2,
        offline: 3,
        completed: 4,
        error: 4,
      };
      return (
        priority[a.phase] - priority[b.phase] ||
        b.startedAt - a.startedAt ||
        a.key.localeCompare(b.key)
      );
    });
}

/** Only settle work observed live on this device; cached historical work never alerts. */
export function completedNowBarRows(
  previous: ReadonlyArray<NowBarRow>,
  threads: ReadonlyArray<EnvironmentThreadShell>,
  connected: ReadonlySet<string>,
): NowBarRow[] {
  const byKey = new Map(threads.map((thread) => [threadKey(thread), thread]));
  return previous.flatMap((row) => {
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
        phase: state === "error" ? ("error" as const) : ("completed" as const),
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
