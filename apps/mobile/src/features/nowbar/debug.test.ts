import { describe, expect, it } from "vite-plus/test";
import { debugModel, debugRow, debugStates } from "./debug";

describe("Now Bar lab fixtures", () => {
  it("covers every live, attention and terminal state with isolated identities", () => {
    const rows = debugStates.map((state) => debugRow(state.id, 1_000_000));
    expect(new Set(rows.map((row) => row.phase))).toEqual(
      new Set(["working", "attention", "monitoring", "offline", "completed", "error", "stopped"]),
    );
    expect(rows.filter((row) => row.phase === "attention").map((row) => row.kind)).toEqual([
      "approval",
      "input",
      "plan",
    ]);
    expect(new Set(rows.map((row) => row.key)).size).toBe(rows.length);
    expect(rows.every((row) => JSON.parse(row.key)[0] === "nowbar-lab")).toBe(true);
  });
  it("uses catalog model names for samples and keeps the title independent of state", () => {
    const catalogs = [
      {
        providers: [
          {
            instanceId: "custom",
            driver: "codex",
            models: [
              { slug: "old", name: "Old model" },
              { slug: "current", name: "Current model", isDefault: true },
            ],
          },
        ],
      },
    ];
    expect(debugModel(catalogs, "OpenAI")).toEqual({
      provider: "codex",
      model: "current",
      modelLabel: "Current model",
    });
    expect(debugModel(catalogs, "Claude")).toEqual({
      provider: "claudeAgent",
      model: "",
      modelLabel: "Example Claude model",
    });
    expect(new Set(debugStates.map((state) => debugRow(state.id, 1_000_000).title))).toEqual(
      new Set(["Example task"]),
    );
  });
  it("clamps interactive progress and never gives non-progress states a fake percentage", () => {
    expect(debugRow("progress", 1_000_000, 99).completed).toBe(8);
    expect(debugRow("progress", 1_000_000, -2).completed).toBe(0);
    for (const state of debugStates.filter((state) => state.id !== "progress")) {
      expect(debugRow(state.id, 1_000_000).total).toBe(0);
    }
  });
});
