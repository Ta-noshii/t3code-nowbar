import { useEffect, useMemo } from "react";
import { AppState } from "react-native";
import { useProjects, useServerConfigs, useThreadShells } from "../../state/entities";
import { useWorkspaceState } from "../../state/workspace";
import { projectNowBarRows } from "./model";
import { nowBarNative, useNowBarPreferences } from "./native";
import { checkNowBarUpdate } from "./updates";
import { useNowBarUnread } from "./unread";

export function NowBarCoordinator() {
  const threads = useThreadShells();
  const catalogs = useServerConfigs();
  const projects = useProjects();
  const { environments } = useWorkspaceState();
  const preferences = useNowBarPreferences();
  const unread = useNowBarUnread();
  const connected = useMemo(
    () =>
      new Set(
        environments.filter((e) => e.connectionState === "connected").map((e) => e.environmentId),
      ),
    [environments],
  );
  const rows = useMemo(
    () => projectNowBarRows(threads, projects, connected, unread, catalogs),
    [threads, projects, connected, unread, catalogs],
  );
  const payload = JSON.stringify(rows);

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
    const heartbeat = nowBarNative.addListener("heartbeat", publish);
    // The same snapshot renews the native freshness lease. The foreground
    // service keeps this JS runtime alive while monitored work exists.
    const timer = preferences.enabled && rows.length > 0 ? setInterval(publish, 20_000) : undefined;
    const listener = AppState.addEventListener("change", (state) => {
      if (state === "active") publish();
    });
    return () => {
      heartbeat.remove();
      if (timer) clearInterval(timer);
      listener.remove();
    };
  }, [payload, preferences.enabled, rows.length]);

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
