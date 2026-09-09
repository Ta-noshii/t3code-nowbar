package expo.modules.t3nowbar

import org.junit.Assert.*
import org.junit.Test

class NowBarPolicyTest {
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
