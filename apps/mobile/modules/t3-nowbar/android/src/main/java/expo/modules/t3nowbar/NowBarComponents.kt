package expo.modules.t3nowbar

import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

/** Samsung's custom slot is separate from Android customContentView, which would lose promotion. */
object NowBarComponents {
  fun attach(builder: NotificationCompat.Builder, context: Context, title: String, phase: String,
    color: Int, completed: Int, total: Int, count: Int, open: PendingIntent, enabled: Boolean) {
    if (!enabled) return
    val views = RemoteViews(context.packageName, R.layout.nowbar_components)
    views.setImageViewBitmap(R.id.nowbar_emblem, NowBarArtwork.emblem(phase, color,
      if (phase == "completed") 100 else NowBarPolicy.progress(completed, total)))
    views.setTextViewText(R.id.nowbar_task, title)
    views.setTextViewText(R.id.nowbar_badge, NowBarPolicy.label(phase))
    views.setTextColor(R.id.nowbar_badge, color)
    views.setTextViewText(R.id.nowbar_metric, if (total > 0) "$completed/$total" else if (count > 1) "$count agents" else "")
    val ids = listOf(R.id.nowbar_step_1, R.id.nowbar_step_2, R.id.nowbar_step_3, R.id.nowbar_step_4,
      R.id.nowbar_step_5, R.id.nowbar_step_6, R.id.nowbar_step_7, R.id.nowbar_step_8)
    val segments = total.coerceIn(0, ids.size)
    views.setViewVisibility(R.id.nowbar_segments, if (segments > 0) View.VISIBLE else View.GONE)
    ids.forEachIndexed { index, id ->
      views.setViewVisibility(id, if (index < segments) View.VISIBLE else View.GONE)
      val filled = total > 0 && (index + 1).toLong() * total <= completed.toLong() * segments
      views.setInt(id, "setColorFilter", if (filled) color else Color.parseColor("#4B435D"))
    }
    views.setContentDescription(R.id.nowbar_segments, "$completed of $total plan steps complete")
    views.setTextViewText(R.id.nowbar_hint, when (phase) {
      "approval" -> "REVIEW REQUEST  ›"
      "input" -> "ANSWER QUESTION  ›"
      "plan" -> "OPEN PROPOSED PLAN  ›"
      "completed" -> "UNREAD RESULT  •  TAP TO REVIEW"
      "error" -> "INSPECT ERROR  ›"
      "stopped" -> "REVIEW STOPPED RUN  ›"
      "offline" -> "RECONNECT  ›"
      "monitoring" -> "WATCHING FOR CHANGES"
      else -> if (total > 0) "PLAN PROGRESS" else "LIVE AGENT SESSION"
    })
    views.setOnClickPendingIntent(R.id.nowbar_component_root, open)
    builder.addExtras(Bundle().apply {
      val prefix = "android.ongoingActivityNoti."
      putInt(prefix + "style", 1)
      putParcelable(prefix + "chronometerRemoteView", views)
      putCharSequence(prefix + "chronometerRemoteViewTag", "t3_nowbar_components_v1")
      putInt(prefix + "chronometerRemoteViewPosition", 1)
      putInt(prefix + "nowbarChronometerPosition", 1)
      putInt(prefix + "actionType", 1)
      putInt(prefix + "actionPrimarySet", 0)
      putString(prefix + "chipExpandedText", NowBarPolicy.chip(phase, count, completed, total))
    })
  }
}
