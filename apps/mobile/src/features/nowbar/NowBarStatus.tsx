import { useAtomValue } from "@effect/atom-react";
import { agentStatusUpdates } from "@t3tools/client-runtime/nowbar";
import { subscribeDynamic } from "@t3tools/client-runtime/rpc";
import { createEnvironmentSubscriptionAtomFamily } from "@t3tools/client-runtime/state/runtime";
import { ORCHESTRATION_WS_METHODS, type EnvironmentId, type ThreadId } from "@t3tools/contracts";
import * as Effect from "effect/Effect";
import * as Option from "effect/Option";
import { AsyncResult } from "effect/unstable/reactivity";
import { useEffect } from "react";
import { connectionAtomRuntime } from "../../connection/runtime";

const statusAtom = createEnvironmentSubscriptionAtomFamily(connectionAtomRuntime, {
  label: "nowbar-agent-status",
  idleTtlMs: 0,
  subscribe: (input: { threadId: ThreadId; turnId: string }) =>
    agentStatusUpdates(
      subscribeDynamic(ORCHESTRATION_WS_METHODS.subscribeThread, () =>
        Effect.succeed({ threadId: input.threadId, turnLimit: 1 }),
      ),
      input.turnId,
    ),
});

export function NowBarStatus({
  environmentId,
  threadId,
  turnId,
  rowKey,
  onStatus,
}: {
  environmentId: EnvironmentId;
  threadId: ThreadId;
  turnId: string;
  rowKey: string;
  onStatus: (key: string, text: string | null) => void;
}) {
  const result = useAtomValue(statusAtom({ environmentId, input: { threadId, turnId } }));
  const text = Option.getOrElse(AsyncResult.value(result), () => "");
  useEffect(() => {
    onStatus(rowKey, text);
  }, [rowKey, text, onStatus]);
  useEffect(() => () => onStatus(rowKey, null), [rowKey, onStatus]);
  return null;
}
