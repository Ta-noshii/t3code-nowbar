package expo.modules.t3nowbar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject

class NowBarService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private var rows = emptyList<JSONObject>()
  private var selectedKey: String? = null
  private var lastHeartbeat = 0L
  private var foreground = false
  private var wakeLock: PowerManager.WakeLock? = null
  private val watchdog = object : Runnable {
    override fun run() {
      // React Native suspends JS timers when its Activity backgrounds. A
      // native event keeps the live snapshot lease renewed without relying
      // on setInterval, while a dead JS runtime still expires below.
      runCatching { T3NowBarModule.heartbeat?.invoke() }
      when (NowBarPolicy.freshness(SystemClock.elapsedRealtime() - lastHeartbeat)) {
        "expired" -> stopSelf()
        "stale" -> render(stale = true)
      }
      handler.postDelayed(this, 15_000)
    }
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL, "Live agent work", NotificationManager.IMPORTANCE_DEFAULT).apply {
      description = "Now Bar, lock screen and status chip while you monitor agent work"
      setSound(null, null)
      enableVibration(false)
    })
    manager.createNotificationChannel(NotificationChannel(RESULTS, "Agent results", NotificationManager.IMPORTANCE_DEFAULT))
    lastHeartbeat = SystemClock.elapsedRealtime()
    handler.postDelayed(watchdog, 15_000)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent == null || !prefs(this).getBoolean("enabled", false)) {
      stopSelf()
      return START_NOT_STICKY
    }
    try {
      val incoming = JSONArray(intent.getStringExtra("rows") ?: "[]")
      val all = (0 until incoming.length()).map { incoming.getJSONObject(it) }
      val activeKeys = all.map { it.getString("key") }.toSet()
      val suppressed = prefs(this).getStringSet("suppressed", emptySet()).orEmpty().intersect(activeKeys)
      prefs(this).edit().putStringSet("suppressed", suppressed).apply()
      val previousAttention = rows.filter { it.optString("phase") == "attention" }.map { it.getString("key") }.toSet()
      rows = all.filterNot { it.getString("key") in suppressed }
      rows.firstOrNull { it.optString("phase") == "attention" && it.getString("key") !in previousAttention }
        ?.let { selectedKey = it.getString("key") }
      lastHeartbeat = SystemClock.elapsedRealtime()
      if (rows.isEmpty()) {
        stopSelf()
      } else {
        render()
        if (wakeLock?.isHeld != true) {
          wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:nowbar")
            .apply { acquire(2 * 60 * 60 * 1000L) }
        }
      }
    } catch (_: Exception) {
      stopSelf()
    }
    // A killed process has no authenticated live connection. Do not revive stale data.
    return START_NOT_STICKY
  }

  fun action(action: String) {
    if (action == "unpin") {
      prefs(this).edit().putStringSet("suppressed", rows.map { it.getString("key") }.toSet()).apply()
      stopSelf()
    } else if (action == "refresh") {
      render()
    } else if (action == "next" && rows.isNotEmpty()) {
      val index = rows.indexOfFirst { it.optString("key") == selectedKey }.coerceAtLeast(0)
      selectedKey = rows[(index + 1) % rows.size].getString("key")
      render()
    }
  }

  private fun render(stale: Boolean = false) {
    if (rows.isEmpty()) return
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
      stopSelf()
      return
    }
    val row = rows.firstOrNull { it.optString("key") == selectedKey } ?: rows.first()
    selectedKey = row.getString("key")
    val phase = if (stale) "offline" else row.getString("phase")
    val privateMode = prefs(this).getBoolean("private", false)
    val title = if (privateMode) "T3 Code · Agent work" else row.getString("title")
    val status = when {
      stale || phase == "offline" -> "Connection paused · Open T3 to reconnect"
      privateMode -> if (phase == "attention") "Your agent needs you" else "Agent work in progress"
      else -> row.getString("status")
    }
    val project = if (privateMode) "Private session" else row.optString("project", "T3 Code")
    val color = Color.parseColor(when (phase) {
      "attention" -> "#F4B860"
      "offline" -> "#94A3B8"
      "monitoring" -> "#40DDB5"
      else -> "#A78BFA"
    })
    val total = if (phase == "offline" || privateMode) 0 else row.optInt("total")
    val completed = row.optInt("completed").coerceIn(0, total.coerceAtLeast(0))
    val chip = NowBarPolicy.chip(phase, rows.size, completed, total)
    val open = openIntent(this, row.getString("url"))
    val unpin = actionIntent("unpin")
    val detail = if (rows.size > 1) "$status · ${rows.indexOf(row) + 1}/${rows.size} agents" else status
    val builder = NotificationCompat.Builder(this, CHANNEL)
      .setSmallIcon(R.drawable.nowbar_pulse)
      .setContentTitle(title)
      .setContentText(detail)
      .setSubText(project)
      .setColor(color)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setContentIntent(open)
      .setDeleteIntent(unpin)
      .setRequestPromotedOngoing(true)
      .setShortCriticalText(chip)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .addAction(0, if (phase == "attention") "Review" else "Open thread", open)
    if (rows.size > 1) builder.addAction(0, "Next agent", actionIntent("next"))
    builder.addAction(0, "Unpin", unpin)
    val started = row.optLong("startedAt")
    if (started > 0 && phase != "offline" && phase != "attention") {
      builder.setWhen(started).setUsesChronometer(true).setShowWhen(true)
    } else builder.setShowWhen(false)
    if (total > 0) {
      builder.setStyle(NotificationCompat.ProgressStyle()
        .setProgress(if (total > 100) NowBarPolicy.progress(completed, total) ?: 0 else completed)
        .setProgressSegments(List(total.coerceAtMost(100)) { NotificationCompat.ProgressStyle.Segment(1).setColor(color) })
        .setStyledByProgress(true))
    } else {
      builder.setStyle(NotificationCompat.BigTextStyle().bigText(detail))
    }
    // Keep a standard Android style: custom RemoteViews/colorized/group summaries
    // disqualify Android Live Updates. Samsung consumes these additional extras.
    if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
      val icon = Icon.createWithResource(this, R.drawable.nowbar_pulse)
      builder.addExtras(Bundle().apply {
        val prefix = "android.ongoingActivityNoti."
        putInt(prefix + "style", 1)
        putCharSequence(prefix + "primaryInfo", title)
        putCharSequence(prefix + "secondaryInfo", detail)
        putCharSequence(prefix + "nowbarPrimaryInfo", title)
        putCharSequence(prefix + "nowbarSecondaryInfo", detail)
        putString(prefix + "chipExpandedText", chip)
        putInt(prefix + "chipBgColor", color)
        putParcelable(prefix + "chipIcon", icon)
        putParcelable(prefix + "nowbarIcon", icon)
        putParcelable(prefix + "nowbarPendingIntentOnSubScreen", open)
        if (total > 0) {
          putInt(prefix + "progress", NowBarPolicy.progress(completed, total) ?: 0)
          putInt(prefix + "progressMax", 100)
        }
      })
    }
    val publicVersion = NotificationCompat.Builder(this, CHANNEL)
      .setSmallIcon(R.drawable.nowbar_pulse).setContentTitle("T3 Code Now Bar")
      .setContentText(if (phase == "attention") "Your agent needs you" else "Agent work in progress")
      .setOngoing(true).build()
    builder.setPublicVersion(publicVersion)
    val notification = builder.build()
    if (!foreground) {
      startForeground(LIVE_ID, notification)
      foreground = true
    } else getSystemService(NotificationManager::class.java).notify(LIVE_ID, notification)
  }

  private fun actionIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
    this, action.hashCode(), Intent(this, NowBarActionReceiver::class.java).setAction(action),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    if (wakeLock?.isHeld == true) wakeLock?.release()
    wakeLock = null
    instance = null
    stopForeground(STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val CHANNEL = "nowbar-live-v1"
    const val RESULTS = "nowbar-results-v1"
    const val LIVE_ID = 76326
    @Volatile var instance: NowBarService? = null
      private set

    fun prefs(context: Context) = context.getSharedPreferences("t3-nowbar", Context.MODE_PRIVATE)

    fun openIntent(context: Context, url: String): PendingIntent {
      require(url.startsWith("t3code-nowbar://threads/"))
      val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        .setAction(Intent.ACTION_VIEW).setData(Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      return PendingIntent.getActivity(context, url.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun result(context: Context, json: String) {
      if (!prefs(context).getBoolean("enabled", false) || !prefs(context).getBoolean("results", true)) return
      if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
      val row = JSONObject(json)
      if (row.getString("key") in prefs(context).getStringSet("suppressed", emptySet()).orEmpty()) return
      val privateMode = prefs(context).getBoolean("private", false)
      val notification = NotificationCompat.Builder(context, RESULTS)
        .setSmallIcon(R.drawable.nowbar_pulse)
        .setContentTitle(if (privateMode) "T3 Code" else row.getString("title"))
        .setContentText(row.getString("status"))
        .setColor(Color.parseColor(if (row.optString("phase") == "error") "#FB7185" else "#40DDB5"))
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setContentIntent(openIntent(context, row.getString("url")))
        .setAutoCancel(true).setTimeoutAfter(120_000).build()
      context.getSystemService(NotificationManager::class.java).notify(row.getString("key").hashCode(), notification)
    }
  }
}

class NowBarActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    NowBarService.instance?.action(intent.action ?: return)
  }
}
