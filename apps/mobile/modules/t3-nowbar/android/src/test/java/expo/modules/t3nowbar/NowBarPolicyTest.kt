package expo.modules.t3nowbar

import org.junit.Assert.*
import org.junit.Test

class NowBarPolicyTest {
  @Test fun everyActionableStateHasASpecificLabelAndChip() {
    assertEquals("ANSWER NEEDED", NowBarPolicy.label("input"))
    assertEquals("Reply", NowBarPolicy.chip("input", 4, 2, 5))
    assertEquals("APPROVAL NEEDED", NowBarPolicy.label("approval"))
    assertEquals("PLAN READY", NowBarPolicy.label("plan"))
    assertEquals("READY TO REVIEW · unread", NowBarPolicy.summary("completed", 1_000, 999_000, 5, 5, 1))
    assertEquals("NEEDS A LOOK · unread", NowBarPolicy.summary("error", 1_000, 999_000, 2, 5, 1))
    assertEquals("STOPPED · unread", NowBarPolicy.summary("stopped", 1_000, 999_000, 2, 5, 1))
    assertEquals("ANSWER NEEDED · 2/5 steps", NowBarPolicy.summary("input", 1_000, 999_000, 2, 5, 1))
  }
  @Test fun compactSummaryUsesRealProgressAndNeverRunsAFutureTimer() {
    assertEquals("WORKING · 2m · 3/5 steps · 2 agents", NowBarPolicy.summary("working", 1_000, 121_000, 3, 5, 2))
    assertEquals("WORKING", NowBarPolicy.summary("working", 500, 100, 0, 0, 1))
    assertEquals("RECONNECT · 2 agents", NowBarPolicy.summary("offline", 1_000, 121_000, 3, 5, 2))
    assertEquals("1h 5m", NowBarPolicy.elapsed(1_000, 3_901_000))
    assertEquals("NEEDS YOU · just started", NowBarPolicy.summary("attention", 1_000, 2_000, 0, 0, 1))
  }
  @Test fun customContextDoesNotRepeatStateProgressOrUnread() {
    assertEquals("My project · 2m", NowBarPolicy.contextSummary("My project", "working", 1_000, 121_000))
    for (phase in listOf("approval", "input", "plan", "completed", "error", "stopped", "offline"))
      assertEquals("My project", NowBarPolicy.contextSummary("My project", phase, 1_000, 121_000))
  }
  @Test fun stalledConnectionsCannotKeepShowingLiveWork() {
    assertEquals("fresh", NowBarPolicy.freshness(74_999))
    assertEquals("stale", NowBarPolicy.freshness(75_000))
    assertEquals("expired", NowBarPolicy.freshness(300_000))
  }
  @Test fun attentionAndDisconnectOverrideProgress() {
    assertEquals("Review", NowBarPolicy.chip("attention", 4, 2, 5))
    assertEquals("Offline", NowBarPolicy.chip("offline", 4, 2, 5))
    assertEquals("4 live", NowBarPolicy.chip("working", 4, 2, 5))
    assertEquals("40%", NowBarPolicy.chip("working", 1, 2, 5))
  }
  @Test fun unknownProgressIsNeverFabricated() {
    assertNull(NowBarPolicy.progress(0, 0))
    assertEquals(100, NowBarPolicy.progress(9, 4))
    assertEquals(0, NowBarPolicy.progress(-1, 4))
    assertEquals("Coding", NowBarPolicy.chip("working", 1, 0, 0))
  }
}
