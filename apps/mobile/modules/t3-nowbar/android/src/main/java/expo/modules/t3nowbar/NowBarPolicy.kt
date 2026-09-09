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

  fun label(phase: String): String = when (phase) {
    "approval" -> "APPROVAL NEEDED"
    "input" -> "ANSWER NEEDED"
    "plan" -> "PLAN READY"
    "background" -> "BACKGROUND WORK"
    "stopped" -> "STOPPED"
    "completed" -> "READY TO REVIEW"
    "error" -> "NEEDS A LOOK"
    "attention" -> "NEEDS YOU"
    "offline" -> "RECONNECT"
    "monitoring" -> "WATCHING"
    else -> "WORKING"
  }

  fun elapsed(startedAt: Long, now: Long): String? {
    if (startedAt <= 0 || startedAt > now) return null
    val minutes = (now - startedAt) / 60_000
    return when {
      minutes < 1 -> "just started"
      minutes < 60 -> "${minutes}m"
      else -> "${minutes / 60}h ${minutes % 60}m"
    }
  }

  fun summary(phase: String, startedAt: Long, now: Long, completed: Int, total: Int, count: Int): String =
    listOfNotNull(
      label(phase),
      if (phase in listOf("offline", "completed", "error", "stopped", "approval", "input", "plan")) null else elapsed(startedAt, now),
      if (phase in listOf("completed", "error", "stopped")) "unread" else if (total > 0 && phase != "offline") "${completed.coerceIn(0, total)}/$total steps" else null,
      if (count > 1) "$count agents" else null,
    ).joinToString(" · ")

  fun contextSummary(project: String, phase: String, startedAt: Long, now: Long): String =
    listOfNotNull(project.takeIf { it.isNotBlank() },
      if (phase in listOf("working", "background", "monitoring")) elapsed(startedAt, now) else null,
    ).joinToString(" · ")

  fun chip(phase: String, count: Int, completed: Int, total: Int): String = when {
    phase == "approval" -> "Allow?"
    phase == "input" -> "Reply"
    phase == "plan" -> "Plan"
    phase == "stopped" -> "Stopped"
    phase == "completed" -> "Ready"
    phase == "error" -> "Error"
    phase == "offline" -> "Offline"
    phase == "attention" -> "Review"
    count > 1 -> "$count live"
    total > 0 -> "${progress(completed, total)}%"
    phase == "monitoring" -> "Watch"
    else -> "Coding"
  }
}
