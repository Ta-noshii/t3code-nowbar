import { useEffect, useMemo, useRef } from "react";
import { AppState } from "react-native";
import { useProjects, useThreadShells } from "../../state/entities";
import { useWorkspaceState } from "../../state/workspace";
import { completedNowBarRows, projectNowBarRows, type NowBarRow } from "./model";
import { nowBarNative, useNowBarPreferences } from "./native";
import { checkNowBarUpdate } from "./updates";

export function NowBarCoordinator() {
  const threads = useThreadShells();
  const projects = useProjects();
  const { environments } = useWorkspaceState();
  const preferences = useNowBarPreferences();
  const previous = useRef<NowBarRow[]>([]);
  const connected = useMemo(
    () =>
      new Set(
        environments.filter((e) => e.connectionState === "connected").map((e) => e.environmentId),
      ),
    [environments],
  );
  const rows = useMemo(
    () => projectNowBarRows(threads, projects, connected),
    [threads, projects, connected],
  );
  const payload = JSON.stringify(rows);

  useEffect(() => {
    if (!nowBarNative) return;
    if (preferences.enabled) {
      for (const row of completedNowBarRows(previous.current, threads, connected))
        nowBarNative.result(JSON.stringify(row));
      previous.current = rows;
    } else previous.current = [];
  }, [rows, threads, connected, preferences.enabled]);

  useEffect(() => {
    if (!nowBarNative) return;
    const publish = () => {
      try {
        nowBarNative?.publish(preferences.enabled ? payload : "[]");
      } catch (error) {
        console.warn("[nowbar] Could not refresh notification", error);
      }
    };
    publish();
    // The same snapshot renews the native freshness lease. The foreground
    // service keeps this JS runtime alive while monitored work exists.
    const timer = preferences.enabled && rows.length > 0 ? setInterval(publish, 20_000) : undefined;
    const listener = AppState.addEventListener("change", (state) => {
      if (state === "active") publish();
    });
    return () => {
      if (timer) clearInterval(timer);
      listener.remove();
    };
  }, [payload, preferences.enabled, preferences.private, rows.length]);

  useEffect(() => {
    if (!nowBarNative || !preferences.updates) return;
    void checkNowBarUpdate(false);
    const listener = AppState.addEventListener("change", (state) => {
      if (state === "active") void checkNowBarUpdate(false);
    });
    return () => listener.remove();
  }, [preferences.updates]);
  useEffect(
    () => () => {
      nowBarNative?.publish("[]");
    },
    [],
  );
  return null;
}
