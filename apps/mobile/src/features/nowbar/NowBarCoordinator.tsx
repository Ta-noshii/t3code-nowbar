import { useCallback, useEffect, useMemo, useState } from "react";
import { AppState } from "react-native";
import { useProjects, useServerConfigs, useThreadShells } from "../../state/entities";
import { useWorkspaceState } from "../../state/workspace";
import { NowBarStatus } from "./NowBarStatus";
import { projectNowBarRows, threadKey } from "./model";
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
  const [statuses, setStatuses] = useState<ReadonlyMap<string, string>>(new Map());
  const onStatus = useCallback((key: string, text: string | null) => {
    setStatuses((previous) => {
      if ((previous.get(key) ?? null) === (text || null)) return previous;
      const next = new Map(previous);
      if (text) next.set(key, text);
      else next.delete(key);
      return next;
    });
  }, []);
  const connected = useMemo(
    () =>
      new Set(
        environments.filter((e) => e.connectionState === "connected").map((e) => e.environmentId),
      ),
    [environments],
  );
  const rows = useMemo(
    () => projectNowBarRows(threads, projects, connected, unread, catalogs, statuses),
    [threads, projects, connected, unread, catalogs, statuses],
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
  const visible = new Set(
    rows
      .slice(0, 3)
      .filter((row) => row.phase === "working")
      .map((row) => row.key),
  );
  return preferences.enabled &&
    preferences.custom &&
    preferences.expanded &&
    !preferences.private ? (
    <>
      {threads
        .filter(
          (thread) => visible.has(threadKey(thread)) && thread.latestTurn?.state === "running",
        )
        .map((thread) => (
          <NowBarStatus
            key={threadKey(thread)}
            environmentId={thread.environmentId}
            threadId={thread.id}
            turnId={thread.latestTurn!.turnId}
            rowKey={threadKey(thread)}
            onStatus={onStatus}
          />
        ))}
    </>
  ) : null;
}
