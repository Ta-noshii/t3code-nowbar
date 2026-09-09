import type { EnvironmentThreadShell } from "@t3tools/client-runtime/state/shell";
import { EnvironmentId, ProjectId, ProviderInstanceId, ThreadId, TurnId } from "@t3tools/contracts";
import { describe, expect, it } from "vite-plus/test";
import { completedNowBarRows, projectNowBarRows, readIdentity } from "./model";

const environmentId = EnvironmentId.make("laptop");
const connected = new Set([environmentId]);
function thread(patch: Partial<EnvironmentThreadShell> = {}): EnvironmentThreadShell {
  return {
    environmentId,
    id: ThreadId.make("task"),
    projectId: ProjectId.make("project"),
    title: "Build a feature",
    modelSelection: { instanceId: ProviderInstanceId.make("codex"), model: "test" },
    runtimeMode: "full-access",
    interactionMode: "default",
    branch: null,
    worktreePath: null,
    latestTurn: {
      turnId: TurnId.make("turn"),
      state: "running",
      requestedAt: "2026-09-09T10:00:00Z",
      startedAt: "2026-09-09T10:00:01Z",
      completedAt: null,
      assistantMessageId: null,
    },
    createdAt: "2026-09-09T10:00:00Z",
    updatedAt: "2026-09-09T10:00:02Z",
    archivedAt: null,
    settledOverride: null,
    settledAt: null,
    session: null,
    latestUserMessageAt: null,
    hasPendingApprovals: false,
    hasPendingUserInput: false,
    hasActionableProposedPlan: false,
    ...patch,
  };
}

describe("Now Bar agent projection", () => {
  it("shares active provider, model and event time with native and host push consumers", () => {
    const running = thread({
      modelSelection: { instanceId: ProviderInstanceId.make("cursor"), model: "claude-sonnet" },
    });
    expect(projectNowBarRows([running], [], connected)[0]).toMatchObject({
      provider: "cursor",
      model: "claude-sonnet",
      eventAt: Date.parse(running.latestTurn!.requestedAt),
    });
    const finished = {
      ...running,
      latestTurn: {
        ...running.latestTurn!,
        state: "completed" as const,
        completedAt: "2026-09-09T10:05:00Z",
      },
    };
    expect(
      projectNowBarRows([finished], [], connected, { since: 0, readTurns: {} })[0]?.eventAt,
    ).toBe(Date.parse(finished.latestTurn.completedAt));
    expect(
      projectNowBarRows([{ ...running, updatedAt: "2026-09-09T10:01:00Z" }], [], connected),
    ).toEqual(projectNowBarRows([running], [], connected));
  });
  it("resolves exact names in the thread's environment and provider, preserving unknown model IDs", () => {
    const catalogs = new Map([
      [
        environmentId,
        {
          providers: [
            {
              instanceId: "codex",
              driver: "codex",
              models: [{ slug: "test", name: "GPT Display Name" }],
            },
            {
              instanceId: "custom",
              driver: "cursor",
              models: [{ slug: "test", name: "Claude Display Name" }],
            },
          ],
        },
      ],
    ]);
    const selected = thread({
      modelSelection: { instanceId: ProviderInstanceId.make("custom"), model: "test" },
    });
    expect(projectNowBarRows([selected], [], connected, undefined, catalogs)[0]).toMatchObject({
      provider: "cursor",
      model: "test",
      modelLabel: "Claude Display Name",
    });
    expect(projectNowBarRows([thread()], [], connected, undefined, catalogs)[0]?.modelLabel).toBe(
      "GPT Display Name",
    );
    const unknown = thread({
      modelSelection: { instanceId: ProviderInstanceId.make("codex"), model: "private-model-v2" },
    });
    expect(projectNowBarRows([unknown], [], connected, undefined, catalogs)[0]?.modelLabel).toBe(
      "private-model-v2",
    );
    expect(projectNowBarRows([thread()], [], connected)[0]?.modelLabel).toBe("test");
  });
  it("distinguishes approval, question, plan review and background work", () => {
    const cases = [
      { hasPendingApprovals: true },
      { hasPendingUserInput: true },
      { hasActionableProposedPlan: true },
      { backgroundLiveness: "working" as const },
    ];
    expect(
      cases.map((patch) => projectNowBarRows([thread(patch)], [], connected)[0]?.kind),
    ).toEqual(["approval", "input", "plan", "background"]);
  });
  it("retains new unread completions until the matching turn is read, including offline", () => {
    const finished = thread({
      latestTurn: {
        ...thread().latestTurn!,
        state: "completed",
        completedAt: "2026-09-09T10:05:00Z",
      },
    });
    const unread = { since: Date.parse("2026-09-09T10:00:00Z"), readTurns: {} };
    const rows = projectNowBarRows([finished], [], connected, unread);
    expect(rows[0]?.phase).toBe("completed");
    expect(projectNowBarRows([finished], [], new Set(), unread)[0]?.phase).toBe("completed");
    expect(
      projectNowBarRows([finished], [], connected, {
        ...unread,
        readTurns: { [readIdentity(finished)]: finished.latestTurn!.turnId },
      }),
    ).toEqual([]);
    expect(completedNowBarRows(rows, [finished], connected)).toEqual([]);
    expect(
      projectNowBarRows([finished], [], connected, {
        ...unread,
        since: Date.parse("2026-09-09T11:00:00Z"),
      }),
    ).toEqual([]);
    expect(
      projectNowBarRows(
        [{ ...finished, archivedAt: "2026-09-09T11:00:00Z" }],
        [],
        connected,
        unread,
      ),
    ).toEqual([]);
  });
  it("read receipts do not suppress a later turn or a matching ID in another environment", () => {
    const finished = thread({
      latestTurn: { ...thread().latestTurn!, state: "error", completedAt: "2026-09-09T10:05:00Z" },
    });
    const unread = { since: 0, readTurns: { [readIdentity(finished)]: "old-turn" } };
    expect(projectNowBarRows([finished], [], connected, unread)[0]?.phase).toBe("error");
    const other = { ...finished, environmentId: EnvironmentId.make("other") };
    expect(
      projectNowBarRows([other], [], connected, {
        since: 0,
        readTurns: { [readIdentity(finished)]: finished.latestTurn!.turnId },
      })[0]?.phase,
    ).toBe("error");
  });
  it("prioritizes attention and uses real plan progress", () => {
    const rows = projectNowBarRows(
      [
        thread({ planProgress: { step: "Running checks", completedSteps: 3, totalSteps: 5 } }),
        thread({ id: ThreadId.make("approval"), hasPendingApprovals: true }),
      ],
      [],
      connected,
    );
    expect(rows.map((row) => row.phase)).toEqual(["attention", "working"]);
    expect(rows[1]).toMatchObject({ status: "Running checks", completed: 3, total: 5 });
    expect(rows[0]?.status).toContain("Approval needed");
  });
  it("does not conflate matching thread IDs in different environments", () => {
    const other = EnvironmentId.make("desktop");
    const rows = projectNowBarRows(
      [thread(), thread({ environmentId: other })],
      [],
      new Set([environmentId, other]),
    );
    expect(new Set(rows.map((row) => row.key)).size).toBe(2);
    expect(new Set(rows.map((row) => row.url)).size).toBe(2);
  });
  it("shows disconnected cached work as offline, never complete", () => {
    const active = projectNowBarRows([thread()], [], connected);
    const rows = projectNowBarRows([thread()], [], new Set());
    expect(rows[0]?.phase).toBe("offline");
    expect(completedNowBarRows(active, [thread()], new Set())).toEqual([]);
  });
  it("only sends completion for the same observed turn after it ends", () => {
    const running = thread();
    const previous = projectNowBarRows([running], [], connected);
    const finished = thread({ latestTurn: { ...running.latestTurn!, state: "completed" } });
    expect(projectNowBarRows([finished], [], connected)).toEqual([]);
    expect(completedNowBarRows(previous, [finished], connected)[0]?.status).toContain(
      "Work complete",
    );
    expect(completedNowBarRows([], [finished], connected)).toEqual([]);
    expect(
      completedNowBarRows(
        previous,
        [thread({ latestTurn: { ...finished.latestTurn!, turnId: TurnId.make("new") } })],
        connected,
      ),
    ).toEqual([]);
  });
  it("keeps background agents and user questions live after the main turn ends", () => {
    const finished = { ...thread().latestTurn!, state: "completed" as const };
    expect(
      projectNowBarRows(
        [thread({ latestTurn: finished, backgroundLiveness: "monitoring" })],
        [],
        connected,
      )[0]?.phase,
    ).toBe("monitoring");
    expect(
      projectNowBarRows(
        [thread({ latestTurn: finished, hasPendingUserInput: true })],
        [],
        connected,
      )[0]?.phase,
    ).toBe("attention");
  });
  it("does not invent a progress percentage or include archived work", () => {
    expect(projectNowBarRows([thread()], [], connected)[0]?.total).toBe(0);
    expect(
      projectNowBarRows([thread({ archivedAt: "2026-09-09T12:00:00Z" })], [], connected),
    ).toEqual([]);
  });
});
