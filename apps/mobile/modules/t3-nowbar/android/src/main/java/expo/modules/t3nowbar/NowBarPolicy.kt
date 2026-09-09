package expo.modules.t3nowbar

internal object NowBarPolicy {
  const val STALE_AFTER_MS = 75_000L
  const val STOP_AFTER_MS = 300_000L

  fun freshness(ageMs: Long): String = when {
    ageMs >= STOP_AFTER_MS -> "expired"
    ageMs >= STALE_AFTER_MS -> "stale"
    else -> "fresh"
  }

  fun progress(completed: Int, total: Int): Int? =
    if (total <= 0) null else ((completed.coerceIn(0, total).toLong() * 100) / total).toInt()

  fun chip(phase: String, count: Int, completed: Int, total: Int): String = when {
    phase == "offline" -> "Offline"
    phase == "attention" -> "Review"
    count > 1 -> "$count live"
    total > 0 -> "${progress(completed, total)}%"
    phase == "monitoring" -> "Watch"
    else -> "Coding"
  }
}
