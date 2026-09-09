package expo.modules.t3nowbar

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

/** Shared across JS-driven monitoring and FCM so a handover cannot double-alert. */
object NowBarAlerts {
  fun signature(row: JSONObject) = row.optString("phase") + ":" + row.optString("kind")

  fun eligible(row: JSONObject, previous: String?, now: Long, attention: Boolean, results: Boolean): Boolean {
    val phase = row.optString("phase")
    if (signature(row) == previous) return false
    if (phase == "attention") return attention
    if (phase !in listOf("completed", "error", "stopped") || !results) return false
    // Do not announce a backlog of cached completions on upgrade/cold start.
    return previous != null || now - row.optLong("eventAt") in 0..600_000
  }

  @Synchronized
  fun consume(context: Context, rows: List<JSONObject>): JSONObject? {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return null
    val prefs = NowBarService.prefs(context)
    val ledger = JSONObject(prefs.getString("alertStates", "{}") ?: "{}")
    var selected: JSONObject? = null
    for (row in rows) {
      if (row.optString("phase") == "offline") continue
      val key = row.getString("key")
      val previous = if (ledger.has(key)) ledger.getString(key) else null
      if (eligible(row, previous, System.currentTimeMillis(), prefs.getBoolean("nudges", true), prefs.getBoolean("results", true)) && selected == null)
        selected = row
      ledger.put(key, signature(row))
    }
    val keys = ledger.keys().asSequence().toList()
    keys.take((keys.size - 256).coerceAtLeast(0)).forEach { ledger.remove(it) }
    prefs.edit().putString("alertStates", ledger.toString()).apply()
    return selected
  }
}
