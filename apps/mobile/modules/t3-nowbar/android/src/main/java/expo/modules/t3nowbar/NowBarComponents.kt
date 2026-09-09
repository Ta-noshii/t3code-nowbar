package expo.modules.t3nowbar

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

/** Samsung's custom slot is separate from Android customContentView, which would lose promotion. */
object NowBarComponents {
  fun views(context: Context, title: String, phase: String, color: Int, completed: Int, total: Int,
    count: Int, provider: String, model: String, detail: String, expanded: Boolean): RemoteViews {
    val views = RemoteViews(context.packageName, if (expanded) R.layout.nowbar_expanded else R.layout.nowbar_components)
    views.setImageViewBitmap(R.id.nowbar_emblem, NowBarBrand.bitmap(context, provider, model))
    views.setTextViewText(R.id.nowbar_task, title)
    views.setTextViewText(R.id.nowbar_badge, when (phase) {
      "approval" -> "Approval needed"
      "input" -> "Needs your answer"
      "plan" -> "Plan ready"
      "completed" -> "Finished · Unread"
      "error" -> "Needs a look"
      "stopped" -> "Stopped"
      "monitoring" -> "Watching"
      "offline" -> "Disconnected"
      "background" -> "Background work"
      else -> "Working"
    })
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
    views.setTextViewText(R.id.nowbar_hint, detail)
    views.setTextViewText(R.id.nowbar_model, model)
    // Leave the surface tap to Samsung's expand/collapse controller.
    return views
  }

  fun attach(builder: NotificationCompat.Builder, context: Context, title: String, phase: String,
    color: Int, completed: Int, total: Int, count: Int, enabled: Boolean,
    provider: String = "", model: String = "", detail: String = "", expandedPreview: Boolean = false) {
    if (!enabled) return
    val compact = views(context, title, phase, color, completed, total, count, provider, model, detail, false)
    val expanded = views(context, title, phase, color, completed, total, count, provider, model, detail, true)
    val views = if (expandedPreview) expanded else if (Build.VERSION.SDK_INT >= 31)
      RemoteViews(mapOf(SizeF(160f, 48f) to compact, SizeF(280f, 110f) to expanded)) else compact
    builder.addExtras(Bundle().apply {
      val prefix = "android.ongoingActivityNoti."
      putInt(prefix + "style", 1)
      putParcelable(prefix + "chronometerRemoteView", views)
      putCharSequence(prefix + "chronometerRemoteViewTag", "t3_nowbar_components_v2")
      putInt(prefix + "chronometerRemoteViewPosition", 1)
      putInt(prefix + "nowbarChronometerPosition", 1)
      putInt(prefix + "actionType", 1)
      putInt(prefix + "actionPrimarySet", 0)
      putString(prefix + "chipExpandedText", NowBarPolicy.chip(phase, count, completed, total))
    })
  }
}
