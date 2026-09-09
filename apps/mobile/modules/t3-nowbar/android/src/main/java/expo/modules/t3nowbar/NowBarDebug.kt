package expo.modules.t3nowbar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject

/** A separate notification ID and store keep fixtures out of real monitoring and read receipts. */
object NowBarDebug {
  const val ID = 76327
  private const val CHANNEL = "nowbar-lab-v1"
  private fun prefs(context: Context) = context.getSharedPreferences("t3-nowbar-lab", Context.MODE_PRIVATE)

  fun show(context: Context, json: String, custom: Boolean) {
    val rows = JSONArray(json)
    require(rows.length() in 1..12)
    prefs(context).edit().putString("rows", json).putInt("index", 0).putBoolean("custom", custom).apply()
    render(context)
  }

  fun next(context: Context) {
    val prefs = prefs(context)
    val rows = JSONArray(prefs.getString("rows", "[]"))
    if (rows.length() == 0) return
    prefs.edit().putInt("index", (prefs.getInt("index", 0) + 1) % rows.length()).apply()
    render(context)
  }

  fun clear(context: Context) {
    context.getSystemService(NotificationManager::class.java).cancel(ID)
    prefs(context).edit().clear().apply()
  }

  fun status(context: Context): Map<String, Boolean> {
    val card = context.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == ID }?.notification
    return mapOf("active" to (card != null),
      "customAttached" to (card?.extras?.containsKey("android.ongoingActivityNoti.chronometerRemoteView") == true),
      "promoted" to (Build.VERSION.SDK_INT >= 36 && card != null && card.flags and Notification.FLAG_PROMOTED_ONGOING != 0))
  }

  private fun action(context: Context, name: String) = PendingIntent.getBroadcast(context, name.hashCode(),
    Intent(context, NowBarActionReceiver::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

  private fun render(context: Context) {
    check(NotificationManagerCompat.from(context).areNotificationsEnabled()) { "Allow notifications in Android settings first." }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL, "Now Bar laboratory", NotificationManager.IMPORTANCE_DEFAULT).apply {
      setSound(null, null); enableVibration(false)
    })
    val prefs = prefs(context)
    val rows = JSONArray(prefs.getString("rows", "[]"))
    if (rows.length() == 0) return
    val row = rows.getJSONObject(prefs.getInt("index", 0).coerceIn(0, rows.length() - 1))
    val phase = row.getString("phase")
    val display = row.optString("kind").ifEmpty { phase }
    val privateMode = NowBarService.prefs(context).getBoolean("private", false)
    val title = if (privateMode) "T3 · Private test" else "TEST · ${row.getString("title")}"
    val color = Color.parseColor(when (display) {
      "approval", "input", "attention" -> "#F4B860"
      "plan" -> "#67D9F5"
      "completed", "monitoring" -> "#40DDB5"
      "error" -> "#FB7185"
      "stopped", "offline" -> "#94A3B8"
      else -> "#A78BFA"
    })
    val total = if (privateMode) 0 else row.optInt("total").coerceAtLeast(0)
    val completed = row.optInt("completed").coerceIn(0, total)
    val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
      .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val open = PendingIntent.getActivity(context, ID, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val summary = NowBarPolicy.summary(display, row.optLong("startedAt"), System.currentTimeMillis(), completed, total, rows.length())
    val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.nowbar_pulse)
      .setContentTitle(title).setContentText(summary).setSubText("Now Bar Lab · Test data")
      .setLargeIcon(NowBarArtwork.emblem(display, color, NowBarPolicy.progress(completed, total)))
      .setColor(color).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setRequestPromotedOngoing(true)
      .setShortCriticalText(NowBarPolicy.chip(display, rows.length(), completed, total))
      .setContentIntent(open).setTimeoutAfter(10 * 60 * 1000L)
      .setDeleteIntent(action(context, "debug-clear"))
      .addAction(0, "Open lab", open)
    if (rows.length() > 1) builder.addAction(0, "Next state", action(context, "debug-next"))
    builder.addAction(0, "Clear test", action(context, "debug-clear"))
    if (total > 0) builder.setStyle(NotificationCompat.ProgressStyle()
      .setProgress(completed).setProgressSegments(List(total.coerceAtMost(8)) { NotificationCompat.ProgressStyle.Segment(1).setColor(color) }))
    else builder.setStyle(NotificationCompat.BigTextStyle().bigText(if (privateMode) summary else "$summary\n${row.getString("status")}"))
    builder.addExtras(Bundle().apply {
      val prefix = "android.ongoingActivityNoti."
      putInt(prefix + "style", 1)
      putString(prefix + "primaryInfo", title)
      putString(prefix + "secondaryInfo", summary)
      putString(prefix + "nowbarPrimaryInfo", title)
      putString(prefix + "nowbarSecondaryInfo", summary)
      putInt(prefix + "chipBgColor", color)
      putString(prefix + "chipExpandedText", NowBarPolicy.chip(display, rows.length(), completed, total))
    })
    NowBarComponents.attach(builder, context, title, display, color, completed, total, rows.length(), open, prefs.getBoolean("custom", true))
    manager.notify(ID, builder.build())
  }
}
