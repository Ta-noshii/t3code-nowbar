package expo.modules.t3agentnotifications

import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import expo.modules.t3nowbar.NowBarService
import expo.modules.t3nowbar.NowBarActionReceiver
import expo.modules.t3nowbar.NowBarAlerts
import expo.modules.t3nowbar.NowBarBrand
import expo.modules.t3nowbar.NowBarComponents
import expo.modules.t3nowbar.NowBarDebug
import android.widget.RemoteViews
import android.widget.FrameLayout
import android.widget.TextView
import android.view.View
import org.robolectric.util.ReflectionHelpers
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 26, 33, 36], manifest = Config.NONE)
class AgentNotificationsTest {
  @Test
  @Config(sdk = [33])
  fun samsungPushCarriesInflatableComponentsWithoutReplacingAndroidContentView() {
    ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "samsung")
    NowBarService.prefs(context).edit().clear().putBoolean("push", true).putBoolean("custom", true).apply()
    val row = JSONObject().put("key", "[\"environment\",\"thread\",\"turn\"]").put("phase", "working")
      .put("title", "Component test").put("status", "Building").put("completed", 3).put("total", 8)
      .put("url", "t3code-nowbar://threads/environment/thread")
    AgentNotifications.receive(context, update("components", true) + ("nowbar_rows" to JSONArray().put(row).toString()))
    val card = manager.activeNotifications.single { it.id == NowBarService.LIVE_ID }.notification
    val views = card.extras.getParcelable<RemoteViews>("android.ongoingActivityNoti.chronometerRemoteView")!!
    val layout = applyAtSize(views, 160f, 48f)
    assertEquals("Component test", layout.findViewById<TextView>(expo.modules.t3nowbar.R.id.nowbar_task).text.toString())
    assertEquals("3/8", layout.findViewById<TextView>(expo.modules.t3nowbar.R.id.nowbar_metric).text.toString())
    assertEquals(View.VISIBLE, layout.findViewById<View>(expo.modules.t3nowbar.R.id.nowbar_segments).visibility)
    assertEquals(1, card.extras.getInt("android.ongoingActivityNoti.nowbarChronometerPosition"))
    assertTrue(card.contentView == null)
    assertTrue(NotificationCompat.isRequestPromotedOngoing(card))
  }

  @Test
  @Config(sdk = [33])
  fun labCanSwitchLayoutsAndClearWithoutChangingLiveCardsOrPreferences() {
    AgentNotifications.receive(context, update("live", true))
    val realIds = manager.activeNotifications.map { it.id }.toSet()
    val before = NowBarService.prefs(context).all.toMap()
    val row = JSONObject().put("phase", "attention").put("kind", "input").put("title", "Question")
      .put("status", "Sample question")
    val json = JSONArray().put(row).toString()
    NowBarDebug.show(context, json, true)
    assertEquals(true, NowBarDebug.status(context)["customAttached"])
    assertEquals(before, NowBarService.prefs(context).all)
    NowBarDebug.show(context, json, false)
    assertEquals(false, NowBarDebug.status(context)["customAttached"])
    NowBarDebug.clear(context)
    assertEquals(realIds, manager.activeNotifications.map { it.id }.toSet())
    assertEquals(before, NowBarService.prefs(context).all)
  }

  @Test
  @Config(sdk = [33])
  fun privateCustomComponentsHideTaskNameAndPlanCounts() {
    NowBarService.prefs(context).edit().putBoolean("private", true).apply()
    val row = JSONObject().put("phase", "working").put("title", "Secret project")
      .put("status", "Secret step").put("completed", 3).put("total", 8)
    NowBarDebug.show(context, JSONArray().put(row).toString(), true)
    val card = manager.activeNotifications.single { it.id == NowBarDebug.ID }.notification
    val views = card.extras.getParcelable<RemoteViews>("android.ongoingActivityNoti.chronometerRemoteView")!!
    val layout = applyAtSize(views, 160f, 48f)
    assertEquals("T3 · Private test", layout.findViewById<TextView>(expo.modules.t3nowbar.R.id.nowbar_task).text.toString())
    assertEquals("", layout.findViewById<TextView>(expo.modules.t3nowbar.R.id.nowbar_metric).text.toString())
    assertEquals(View.GONE, layout.findViewById<View>(expo.modules.t3nowbar.R.id.nowbar_segments).visibility)
  }
  @Test
  @Config(sdk = [33])
  fun customPushRetainsUnreadResultsAndHonorsDismissalAndReadReceipts() {
    val prefs = NowBarService.prefs(context)
    prefs.edit().clear().putBoolean("push", true).putBoolean("pushLive", true).putBoolean("results", false).apply()
    val key = JSONArray().put("environment").put("thread").put("turn").toString()
    val row = JSONObject().put("key", key).put("phase", "completed").put("title", "Private task")
      .put("status", "Ready to review").put("url", "t3code-nowbar://threads/environment/thread")
    val payload = update("unused", false) + ("nowbar_rows" to JSONArray().put(row).toString())
    AgentNotifications.receive(context, payload)
    val card = manager.activeNotifications.single { it.id == NowBarService.LIVE_ID }.notification
    assertTrue(card.flags and Notification.FLAG_ONGOING_EVENT != 0)
    assertTrue(NotificationCompat.isRequestPromotedOngoing(card))
    assertEquals(0L, card.timeoutAfter)
    NowBarActionReceiver().onReceive(context, Intent().setAction("remote-dismiss").putExtra("key", key))
    AgentNotifications.receive(context, payload)
    assertTrue(manager.activeNotifications.isEmpty())
    prefs.edit().remove("suppressed").putString("readTurns", JSONObject().put(JSONArray().put("environment").put("thread").toString(), "turn").toString()).apply()
    AgentNotifications.receive(context, payload)
    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Test
  @Config(sdk = [33])
  fun customPushRespectsReceiverIdentityPrivacyAndDisabledState() {
    val prefs = NowBarService.prefs(context)
    prefs.edit().clear().putBoolean("push", true).putBoolean("private", true).apply()
    val row = JSONObject().put("key", "[\"environment\",\"thread\",\"turn\"]").put("phase", "attention").put("kind", "input")
      .put("title", "Secret task name").put("status", "Secret question").put("url", "t3code-nowbar://threads/environment/thread")
    val payload = update("unused", true) + ("nowbar_rows" to JSONArray().put(row).toString())
    AgentNotifications.receive(context, payload + ("user_id" to "other-user"))
    assertTrue(manager.activeNotifications.isEmpty())
    AgentNotifications.receive(context, payload)
    val card = manager.activeNotifications.single { it.id == NowBarService.LIVE_ID }.notification
    assertEquals("T3 Code · Agent work", card.extras.getString(Notification.EXTRA_TITLE))
    assertTrue(card.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("ANSWER NEEDED"))
    manager.cancelAll()
    prefs.edit().putBoolean("push", false).apply()
    AgentNotifications.receive(context, payload)
    assertTrue(manager.activeNotifications.isEmpty())
  }
  private fun nowBarRow(phase: String, kind: String = "", key: String = "turn") = JSONObject()
    .put("key", key).put("phase", phase).put("kind", kind).put("title", "A task")
    .put("status", "A status").put("url", "t3code-nowbar://threads/environment/thread")
    .put("eventAt", System.currentTimeMillis())

  private fun pushRows(vararg rows: JSONObject) {
    NowBarService.receiveRemoteRows(context, JSONArray(rows.toList()).toString(), System.currentTimeMillis())
  }

  private fun liveCard() = manager.activeNotifications.single { it.id == NowBarService.LIVE_ID }.notification
  private fun assertNudge(card: Notification, expected: Boolean) {
    assertEquals(!expected, card.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    if (expected) assertTrue(card.group != "silent")
    else assertEquals("silent", card.group)
    assertTrue(card.fullScreenIntent == null)
  }

  @Test
  @Config(sdk = [33])
  fun nudgesOncePerTransitionAcrossLocalAndPushUpdates() {
    NowBarService.prefs(context).edit().clear().putBoolean("push", true).apply()
    val working = nowBarRow("working")
    pushRows(working)
    assertNudge(liveCard(), false)
    for ((phase, kind) in listOf("attention" to "approval", "working" to "", "attention" to "input",
      "attention" to "plan", "completed" to "", "error" to "", "stopped" to "")) {
      val row = nowBarRow(phase, kind)
      pushRows(row)
      assertNudge(liveCard(), phase != "working")
      pushRows(row.put("status", "Updated detail"))
      assertNudge(liveCard(), false)
      assertTrue(NowBarAlerts.consume(context, listOf(row)) == null)
    }
    // JS-driven monitoring uses this same persistent ledger before posting.
    val local = nowBarRow("attention", "approval", "next-turn")
    assertEquals(local, NowBarAlerts.consume(context, listOf(local)))
    pushRows(local)
    assertNudge(liveCard(), false)
    assertEquals(1, manager.activeNotifications.size)
    assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(NowBarService.CHANNEL).importance)
    assertTrue(manager.getNotificationChannel(NowBarService.CHANNEL).shouldVibrate())
    assertFalse(manager.getNotificationChannel(NowBarService.CHANNEL).canBypassDnd())
  }

  @Test
  @Config(sdk = [33])
  fun localServiceHandoverKeepsPushAlertsDeduplicatedAndUnreadResultsPinned() {
    NowBarService.prefs(context).edit().clear().putBoolean("push", true).putBoolean("enabled", true).apply()
    val attention = nowBarRow("attention", "approval")
    pushRows(attention)
    assertNudge(liveCard(), true)
    val controller = Robolectric.buildService(NowBarService::class.java).create()
    try {
      controller.get().onStartCommand(Intent(context, NowBarService::class.java)
        .putExtra("rows", JSONArray().put(attention).toString()), 0, 1)
      assertNudge(liveCard(), false)
      val done = nowBarRow("completed")
      controller.get().onStartCommand(Intent(context, NowBarService::class.java)
        .putExtra("rows", JSONArray().put(done).toString()), 0, 2)
      assertNudge(liveCard(), true)
      assertEquals(0L, liveCard().timeoutAfter)
      assertTrue(liveCard().flags and Notification.FLAG_ONGOING_EVENT != 0)
      controller.get().action("refresh")
      assertNudge(liveCard(), false)
    } finally {
      controller.destroy()
    }
    pushRows(nowBarRow("completed"))
    assertNudge(liveCard(), false)
  }

  @Test
  @Config(sdk = [33])
  fun staleCompletionsStayVisibleSilentlyAndOfflineDoesNotRearmAttention() {
    NowBarService.prefs(context).edit().clear().putBoolean("push", true).apply()
    for (phase in listOf("completed", "error", "stopped")) {
      val row = nowBarRow(phase, key = phase).put("eventAt", System.currentTimeMillis() - 600_001)
      pushRows(row)
      assertNudge(liveCard(), false)
      assertEquals(0L, liveCard().timeoutAfter)
    }
    val attention = nowBarRow("attention", "approval")
    pushRows(attention)
    assertNudge(liveCard(), true)
    pushRows(nowBarRow("offline"))
    assertNudge(liveCard(), false)
    pushRows(attention)
    assertNudge(liveCard(), false)
    pushRows(nowBarRow("working"))
    pushRows(attention)
    assertNudge(liveCard(), true)
  }

  @Test
  @Config(sdk = [33])
  fun permissionDenialDoesNotConsumeLocalOrPushNudges() {
    val prefs = NowBarService.prefs(context)
    prefs.edit().clear().putBoolean("push", true).apply()
    val attention = nowBarRow("attention", "input")
    shadowOf(manager).setNotificationsEnabled(false)
    assertTrue(NowBarAlerts.consume(context, listOf(attention)) == null)
    pushRows(attention)
    assertFalse(prefs.contains("alertStates"))
    assertFalse(prefs.contains("lastPushAt"))
    assertTrue(manager.activeNotifications.isEmpty())
    shadowOf(manager).setNotificationsEnabled(true)
    pushRows(attention)
    assertNudge(liveCard(), true)
  }

  @Test
  @Config(sdk = [33])
  fun preferencesSuppressNudgesWithoutHidingResultsOrReplayingOnEnable() {
    val prefs = NowBarService.prefs(context)
    prefs.edit().clear().putBoolean("push", true).putBoolean("nudges", false).putBoolean("results", false).apply()
    pushRows(nowBarRow("attention", "input"))
    assertNudge(liveCard(), false)
    pushRows(nowBarRow("completed"))
    assertNudge(liveCard(), false)
    prefs.edit().putBoolean("nudges", true).putBoolean("results", true).apply()
    pushRows(nowBarRow("completed"))
    assertNudge(liveCard(), false)
    pushRows(nowBarRow("error"))
    assertNudge(liveCard(), true)
    prefs.edit().putBoolean("pushLive", false).apply()
    pushRows(nowBarRow("attention", "plan", "other"))
    val result = manager.activeNotifications.single().notification
    assertEquals(NowBarService.RESULTS, result.channelId)
    assertTrue(manager.getNotificationChannel(NowBarService.RESULTS).shouldVibrate())
  }

  @Test
  @Config(sdk = [33])
  fun postingDoesNotOverrideChannelSettings() {
    NowBarService.prefs(context).edit().clear().putBoolean("push", true).apply()
    val channel = android.app.NotificationChannel(NowBarService.CHANNEL, "Live agent work", NotificationManager.IMPORTANCE_LOW)
    channel.enableVibration(false)
    channel.setSound(null, null)
    manager.createNotificationChannel(channel)
    manager.createNotificationChannel(android.app.NotificationChannel("nowbar-results-v1", "Agent results", NotificationManager.IMPORTANCE_NONE))
    pushRows(nowBarRow("attention", "approval"))
    assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(NowBarService.RESULTS).importance)
    val stored = manager.getNotificationChannel(NowBarService.CHANNEL)
    assertEquals(NotificationManager.IMPORTANCE_LOW, stored.importance)
    assertFalse(stored.shouldVibrate())
    assertTrue(stored.sound == null)
    assertFalse(stored.canBypassDnd())
  }

  @Test
  @Config(sdk = [33])
  fun oneBatchSelectsOneNudgeAndRecordsAllStates() {
    NowBarService.prefs(context).edit().clear().putBoolean("push", true).apply()
    val attention = nowBarRow("attention", "approval", "first")
    val done = nowBarRow("completed", key = "second")
    pushRows(attention, done)
    assertNudge(liveCard(), true)
    assertEquals(1, manager.activeNotifications.size)
    pushRows(done, attention)
    assertNudge(liveCard(), false)
  }

  @Test
  @Config(sdk = [33, 36], qualifiers = "mdpi")
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun compactAndExpandedLayoutsInflateWithRealBrandingAndNoTapInterception() {
    for ((provider, model, resource) in listOf(
      Triple("codex", "gpt-6", expo.modules.t3nowbar.R.drawable.nowbar_openai),
      Triple("cursor", "claude-sonnet", expo.modules.t3nowbar.R.drawable.nowbar_claude),
      Triple("cursor", "auto", expo.modules.t3nowbar.R.drawable.nowbar_cursor),
      Triple("grok", "grok", expo.modules.t3nowbar.R.drawable.nowbar_grok),
      Triple("opencode", "auto", expo.modules.t3nowbar.R.drawable.nowbar_opencode),
      Triple("antigravity", "gemini", expo.modules.t3nowbar.R.drawable.nowbar_pulse))) {
      assertEquals(resource, NowBarBrand.resource(provider, model))
      val bitmap = NowBarBrand.bitmap(context, provider, model)
      val pixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      assertTrue("$provider logo must render visible pixels", pixels.any { android.graphics.Color.alpha(it) > 0 })
    }
    val compact = NowBarComponents.views(context, "A long task name that must ellipsize", "working",
      android.graphics.Color.GREEN, 3, 8, 1, "codex", "gpt-6", "Building the feature", false)
      .apply(context, FrameLayout(context))
    compact.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    compact.layout(0, 0, compact.measuredWidth, compact.measuredHeight)
    assertTrue("Compact height: ${compact.measuredHeight}", compact.measuredHeight in 24..48)
    assertFalse(compact.hasOnClickListeners())
    assertEquals(View.GONE, compact.findViewById<View>(expo.modules.t3nowbar.R.id.nowbar_hint).visibility)
    val longExpanded = NowBarComponents.views(context, "Long task ".repeat(12), "working",
      android.graphics.Color.GREEN, 3, 8, 1, "codex", "gpt-6", "Long status ".repeat(20), true)
      .apply(context, FrameLayout(context))
    longExpanded.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    assertTrue("Expanded height: ${longExpanded.measuredHeight}", longExpanded.measuredHeight <= 110)
    val row = nowBarRow("attention", "plan").put("model", "claude-sonnet").put("status", "Review the proposed plan")
    NowBarDebug.show(context, JSONArray().put(row).toString(), true, true, true)
    val card = manager.activeNotifications.single { it.id == NowBarDebug.ID }.notification
    assertNudge(card, true)
    val expanded = card.extras.getParcelable<RemoteViews>("android.ongoingActivityNoti.chronometerRemoteView")!!
      .apply(context, FrameLayout(context))
    assertEquals("Review the proposed plan", expanded.findViewById<TextView>(expo.modules.t3nowbar.R.id.nowbar_hint).text.toString())
    assertEquals("claude-sonnet", expanded.findViewById<TextView>(expo.modules.t3nowbar.R.id.nowbar_model).text.toString())
    assertFalse(expanded.hasOnClickListeners())
    assertFalse(card.extras.containsKey("android.ongoingActivityNoti.nowbarPendingIntentOnSubScreen"))
    NowBarDebug.next(context)
    assertNudge(manager.activeNotifications.single { it.id == NowBarDebug.ID }.notification, false)
  }

  private fun applyAtSize(views: RemoteViews, width: Float, height: Float): View {
    // Exercise the size selection SystemUI hosts use, which is hidden from the app SDK.
    val selected = ReflectionHelpers.callInstanceMethod<RemoteViews>(views, "getRemoteViewsToApply",
      ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, context),
      ReflectionHelpers.ClassParameter.from(android.util.SizeF::class.java, android.util.SizeF(width, height)))
    return selected.apply(context, FrameLayout(context))
  }

  @Test
  @Config(sdk = [33, 36], qualifiers = "mdpi")
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun notificationFallbackFitsOneLineWhileLargerHostsKeepDetails() {
    for ((phase, count, total) in listOf(Triple("working", 11, 0), Triple("completed", 2, 0),
      Triple("working", 1, 8))) {
      val row = nowBarRow(phase).put("title", "Restore Super Shift Notifications")
        .put("completed", 3).put("total", total).put("model", "claude-sonnet")
      NowBarDebug.show(context, JSONArray(List(count) { row }).toString(), true)
      val card = manager.activeNotifications.single { it.id == NowBarDebug.ID }.notification
      val views = card.extras.getParcelable<RemoteViews>("android.ongoingActivityNoti.chronometerRemoteView")!!
      val fallback = views.apply(context, FrameLayout(context))
      fallback.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
      fallback.layout(0, 0, fallback.measuredWidth, fallback.measuredHeight)
      assertTrue("$phase fallback height ${fallback.measuredHeight} must fit the title slot", fallback.measuredHeight <= 24)
      val title = fallback.findViewById<TextView>(expo.modules.t3nowbar.R.id.nowbar_task)
      assertEquals("TEST · Restore Super Shift Notifications", title.text.toString())
      val bounds = android.graphics.Rect(0, 0, title.width, title.height)
      (fallback as android.view.ViewGroup).offsetDescendantRectToMyCoords(title, bounds)
      assertTrue("Title bounds $bounds must fit the slot", bounds.top >= 0 && bounds.bottom <= 24)
      val compact = applyAtSize(views, 160f, 48f)
      assertEquals(if (total > 0) "3/8" else "$count agents",
        compact.findViewById<TextView>(expo.modules.t3nowbar.R.id.nowbar_metric).text.toString())
      val expanded = applyAtSize(views, 280f, 110f)
      assertEquals(View.VISIBLE, expanded.findViewById<View>(expo.modules.t3nowbar.R.id.nowbar_model).visibility)
      assertEquals("claude-sonnet", expanded.findViewById<TextView>(expo.modules.t3nowbar.R.id.nowbar_model).text.toString())
    }
  }

  private lateinit var context: Application
  private lateinit var manager: NotificationManager
  private lateinit var lifecycle: LifecycleRegistry

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    manager = context.getSystemService(NotificationManager::class.java)
    shadowOf(manager).setNotificationsEnabled(true)
    lifecycle = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry
    lifecycle.currentState = Lifecycle.State.CREATED

    val launcher = ComponentName(context, Activity::class.java)
    shadowOf(context.packageManager).addActivityIfNotPresent(launcher)
    shadowOf(context.packageManager).addIntentFilterForActivity(
      launcher,
      IntentFilter(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
      }
    )
    AgentNotifications.clear(context)
    AgentNotifications.configure(context, "device", "user", "t3code-dev", true)
  }

  private fun update(alertId: String, active: Boolean) = mapOf(
    "device_id" to "device",
    "user_id" to "user",
    "updated_at" to System.currentTimeMillis().toString(),
    "active" to active.toString(),
    "activity_title" to "1 active agent",
    "activity_body" to "Test thread · Working",
    "activity_path" to "/threads/environment/thread",
    "alert_id" to alertId,
    "alert_title" to "Test thread",
    "alert_body" to "Done: Test project",
    "alert_path" to "/threads/environment/thread",
  )

  @Test
  fun alertHistoryEvictsOnlyTheOldestEntryAfterCapacity() {
    lifecycle.currentState = Lifecycle.State.RESUMED
    for (id in 0..64) AgentNotifications.receive(context, update("alert-$id", false))
    lifecycle.currentState = Lifecycle.State.CREATED
    for (id in 1..64) AgentNotifications.receive(context, update("alert-$id", false))
    assertTrue(manager.activeNotifications.isEmpty())
    AgentNotifications.receive(context, update("alert-0", false))
    assertEquals("alert-0".hashCode(), manager.activeNotifications.single().id)
  }

  @Test
  fun missingLauncherDoesNotDiscardTheAlert() {
    shadowOf(context.packageManager).removeActivity(ComponentName(context, Activity::class.java))
    AgentNotifications.receive(context, update("no-launcher", false))
    assertEquals(
      "Test thread",
      manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TITLE)
    )
  }

  @Test
  fun foregroundSuppressesAlertsWhileOngoingActivityStillUpdatesAndClears() {
    lifecycle.currentState = Lifecycle.State.RESUMED

    AgentNotifications.receive(context, update("attention", true))

    val ongoing = manager.activeNotifications.single()
    assertEquals("t3-agent-activity", ongoing.tag)
    assertEquals("1 active agent", ongoing.notification.extras.getString(Notification.EXTRA_TITLE))
    assertTrue(ongoing.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)

    AgentNotifications.receive(context, update("completion", false))

    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Test
  fun backgroundCompletionAlertsAndClearsOngoingActivity() {
    lifecycle.currentState = Lifecycle.State.RESUMED
    AgentNotifications.receive(context, update("running", true))
    lifecycle.currentState = Lifecycle.State.CREATED

    AgentNotifications.receive(context, update("completion", false))

    val alert = manager.activeNotifications.single()
    assertEquals("t3-agent-alert", alert.tag)
    assertEquals("Test thread", alert.notification.extras.getString(Notification.EXTRA_TITLE))
    assertFalse(alert.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
  }

  @Test
  fun retryOfForegroundSuppressedAlertDoesNotAppearAfterBackgrounding() {
    lifecycle.currentState = Lifecycle.State.RESUMED
    val suppressed = update("foreground-completion", false)
    AgentNotifications.receive(context, suppressed)
    lifecycle.currentState = Lifecycle.State.CREATED

    AgentNotifications.receive(context, suppressed)

    assertTrue(manager.activeNotifications.isEmpty())

    AgentNotifications.receive(context, update("later-background-completion", false))

    assertEquals("later-background-completion".hashCode(), manager.activeNotifications.single().id)
  }

  @Test
  fun returningToForegroundSuppressesNewAlertsWithoutRemovingPreviousOnes() {
    AgentNotifications.receive(context, update("background-completion", false))
    lifecycle.currentState = Lifecycle.State.RESUMED

    AgentNotifications.receive(context, update("foreground-completion", false))

    assertEquals("background-completion".hashCode(), manager.activeNotifications.single().id)
  }

  @Test
  fun groupedAlertDisplaysEveryThreadAndRetriesStaySilent() {
    val titles = (1..5).map { "Thread $it " + "x".repeat(111) }.joinToString(", ")
    val grouped = update("group-completion", false) + mapOf(
      "alert_title" to "5 agents finished",
      "alert_body" to titles,
      "alert_path" to "/",
    )

    AgentNotifications.receive(context, grouped)
    AgentNotifications.receive(
      context,
      grouped + ("alert_body" to "A retry must not replace this alert")
    )

    val alert = manager.activeNotifications.single()
    assertEquals("5 agents finished", alert.notification.extras.getString(Notification.EXTRA_TITLE))
    assertEquals(titles, alert.notification.extras.getString(Notification.EXTRA_BIG_TEXT))
    assertEquals("t3code-dev://", shadowOf(alert.notification.contentIntent).savedIntent.dataString)
  }

  @Test
  fun foregroundSuppressedGroupCannotAppearOnBackgroundRetry() {
    val grouped = update("group-attention", true) + mapOf(
      "alert_title" to "2 agents need attention",
      "alert_body" to "First thread, Second thread",
      "alert_path" to "/",
    )
    lifecycle.currentState = Lifecycle.State.RESUMED
    AgentNotifications.receive(context, grouped)
    lifecycle.currentState = Lifecycle.State.CREATED
    AgentNotifications.receive(context, grouped)

    assertEquals("t3-agent-activity", manager.activeNotifications.single().tag)
  }

  @Test
  fun reopeningSameAccountPreservesCardsDeduplicationAndDismissal() {
    val message = update("attention", true)
    AgentNotifications.receive(context, message)
    AgentNotifications.configure(context, "device", "user", "t3code-dev", true)
    assertEquals(2, manager.activeNotifications.size)
    AgentNotifications.dismiss(context)
    AgentNotifications.configure(context, "device", "user", "t3code-dev", true)
    AgentNotifications.receive(context, message)
    assertEquals("t3-agent-alert", manager.activeNotifications.single().tag)
  }

  @Test
  fun changingAccountOrDeviceClearsOldCardsAndRejectsOldPushes() {
    AgentNotifications.receive(context, update("attention", true))
    AgentNotifications.configure(context, "device", "different-user", "t3code-dev", true)
    AgentNotifications.receive(context, update("attention", true))
    assertTrue(manager.activeNotifications.isEmpty())
    AgentNotifications.configure(context, "different-device", "user", "t3code-dev", true)
    AgentNotifications.receive(context, update("attention", true))
    assertTrue(manager.activeNotifications.isEmpty())
    AgentNotifications.clear(context)
    AgentNotifications.receive(context, update("attention", true))
    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Test
  fun expandedActivityShowsFiveRowsAndUsesThePriorityThreadRoute() {
    lifecycle.currentState = Lifecycle.State.RESUMED
    val lines =
      listOf(
        "Approval: First · Project",
        "Input: Second · Project",
        "Failed: Third · Project",
        "Working: Fourth · Project",
        "Done: Fifth · Project"
      )
    AgentNotifications.receive(
      context,
      update("attention", true) +
        lines.mapIndexed { index, line -> "activity_line_$index" to line }.toMap()
    )
    val card = manager.activeNotifications.single().notification
    assertEquals(lines.joinToString("\n"), card.extras.getString(Notification.EXTRA_BIG_TEXT))
    assertEquals(
      "t3code-dev://threads/environment/thread",
      shadowOf(card.contentIntent).savedIntent.dataString
    )
  }

  @Test
  fun quietWorkUsesAbsoluteRelayLifetimeInsteadOfTenMinuteRemoval() {
    lifecycle.currentState = Lifecycle.State.RESUMED
    val expiresAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
    AgentNotifications.receive(
      context,
      update("work", true) + ("activity_expires_at" to expiresAt.toString())
    )
    val card = manager.activeNotifications.single().notification
    assertTimeout(card, 119 * 60 * 1000L..120 * 60 * 1000L)
  }

  @Test
  fun finishedCardIsRetainedSilentlyWithoutOngoingFlagAndExpiresAtTheOriginalDeadline() {
    lifecycle.currentState = Lifecycle.State.RESUMED
    val expiresAt = System.currentTimeMillis() + 15 * 60 * 1000L
    val finished = update("finished", false) + mapOf(
      "activity_title" to "Agent work failed",
      "activity_body" to "Failed: Test thread · Project",
      "activity_expires_at" to expiresAt.toString(),
    )
    AgentNotifications.receive(context, finished)
    val card = manager.activeNotifications.single().notification
    assertEquals("Agent work failed", card.extras.getString(Notification.EXTRA_TITLE))
    assertFalse(card.flags and Notification.FLAG_ONGOING_EVENT != 0)
    assertTimeout(card, 1..15 * 60 * 1000L)
    AgentNotifications.receive(
      context,
      finished + ("activity_expires_at" to (System.currentTimeMillis() - 1).toString())
    )
    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Test
  fun dismissalIncludesFinishedReplaysAndANewRunRearmsTheCard() {
    lifecycle.currentState = Lifecycle.State.RESUMED
    AgentNotifications.receive(context, update("work", true))
    AgentNotifications.dismiss(context)
    val finished =
      update("finished", false) +
        ("activity_expires_at" to (System.currentTimeMillis() + 900000).toString())
    AgentNotifications.receive(context, finished)
    AgentNotifications.receive(context, finished)
    assertTrue(manager.activeNotifications.isEmpty())
    AgentNotifications.receive(context, update("new-work", true))
    assertEquals("t3-agent-activity", manager.activeNotifications.single().tag)
    AgentNotifications.configure(context, "device", "user", "t3code-dev", false)
    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Test
  fun reorderedActivityDoesNotEraseNewerCardOrDropAnIndependentAlert() {
    val now = System.currentTimeMillis()
    AgentNotifications.receive(context, update("new", true) + ("updated_at" to now.toString()))
    AgentNotifications.receive(
      context,
      update("older-alert", false) + ("updated_at" to (now - 1000).toString())
    )
    assertEquals(3, manager.activeNotifications.size)
    assertEquals(1, manager.activeNotifications.count { it.tag == "t3-agent-activity" })
    shadowOf(manager).setNotificationsEnabled(false)
    AgentNotifications.receive(context, update("revoked-permission", true))
    assertEquals(3, manager.activeNotifications.size)
  }

  @Test
  fun longRowsKeepStatusAndBothTitlesWithinTheNotificationWidth() {
    lifecycle.currentState = Lifecycle.State.RESUMED
    val raw = "Approval\t${"Long thread name ".repeat(10)}\t${"Project name ".repeat(10)}"
    AgentNotifications.receive(
      context,
      update("long-work", true) + (0..4).associate { "activity_line_$it" to raw }
    )
    val lines = manager.activeNotifications.single().notification.extras.getString(
      Notification.EXTRA_BIG_TEXT
    )!!.split('\n')
    assertEquals(5, lines.size)
    for (line in lines) {
      assertTrue(line.startsWith("Approval: "))
      assertTrue(line.contains(" · "))
      assertTrue(line.length < raw.length)
      assertFalse(line.contains('\t'))
      assertTrue(line.substringAfter(" · ").isNotBlank())
    }
  }

  private fun assertTimeout(card: Notification, expected: LongRange) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      assertTrue(card.timeoutAfter in expected)
      assertTrue(
        shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty()
      )
    } else {
      val alarm = shadowOf(
        context.getSystemService(AlarmManager::class.java)
      ).scheduledAlarms.single()
      assertTrue(alarm.triggerAtTime - System.currentTimeMillis() in expected)
      assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
    }
  }

  @Test
  @Config(sdk = [24, 25])
  fun legacyExpiryRemovesOnlyTheCardAndCannotRemoveANewerRun() {
    val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    val expiresAt = System.currentTimeMillis() + 900000
    AgentNotifications.receive(
      context,
      update("done", false) + ("activity_expires_at" to expiresAt.toString())
    )
    val oldExpiry = alarms.scheduledAlarms.single().operation!!
    AgentNotifications.receive(context, update("next-run", true))
    assertEquals(1, alarms.scheduledAlarms.size)
    val receiver = AgentActivityExpiryReceiver()
    receiver.onReceive(context, shadowOf(oldExpiry).savedIntent)
    AgentNotifications.expire(context, expiresAt + 60_000)
    assertEquals(1, manager.activeNotifications.count { it.tag == "t3-agent-activity" })
    AgentNotifications.expire(context, expiresAt + 2 * 60 * 60 * 1000L)
    assertTrue(manager.activeNotifications.all { it.tag == "t3-agent-alert" })
    assertTrue(alarms.scheduledAlarms.isEmpty())
  }

  @Test
  @Config(sdk = [24, 25])
  fun legacyExpiryIsCancelledOnDismissDisableAndSignOut() {
    val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    AgentNotifications.receive(context, update("work", true))
    AgentNotifications.dismiss(context)
    assertTrue(alarms.scheduledAlarms.isEmpty())
    AgentNotifications.configure(context, "device", "user", "t3code-dev", false)
    AgentNotifications.configure(context, "device", "user", "t3code-dev", true)
    AgentNotifications.receive(context, update("work", true))
    assertEquals(1, alarms.scheduledAlarms.size)
    AgentNotifications.configure(context, "device", "user", "t3code-dev", false)
    assertTrue(alarms.scheduledAlarms.isEmpty())
    AgentNotifications.configure(context, "device", "user", "t3code-dev", true)
    AgentNotifications.receive(context, update("work", true))
    AgentNotifications.clear(context)
    assertTrue(alarms.scheduledAlarms.isEmpty())
    assertTrue(manager.activeNotifications.isEmpty())
  }

  @Test
  fun alertsAndActivityUseVersionAppropriatePriorityAndPromotion() {
    AgentNotifications.receive(context, update("work", true))
    val alert = manager.activeNotifications.single { it.tag == "t3-agent-alert" }.notification
    val card = manager.activeNotifications.single { it.tag == "t3-agent-activity" }.notification
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      assertEquals(Notification.PRIORITY_HIGH, alert.priority)
      assertTrue(alert.defaults and Notification.DEFAULT_SOUND != 0)
      assertEquals(Notification.PRIORITY_LOW, card.priority)
      assertEquals(0, card.defaults)
    } else {
      assertEquals(
        NotificationManager.IMPORTANCE_HIGH,
        manager.getNotificationChannel(alert.channelId).importance
      )
      assertEquals(
        NotificationManager.IMPORTANCE_LOW,
        manager.getNotificationChannel(card.channelId).importance
      )
    }
    assertTrue(NotificationCompat.isRequestPromotedOngoing(card))
    assertFalse(NotificationCompat.isRequestPromotedOngoing(alert))
    // Robolectric's API 36 image predates the shipped Live Update rules;
    // its hasPromotableCharacteristics() incorrectly requires colorization.
    assertFalse(card.extras.getBoolean(NotificationCompat.EXTRA_COLORIZED))
    assertTrue(card.flags and Notification.FLAG_ONGOING_EVENT != 0)
    assertEquals(Notification.VISIBILITY_PRIVATE, card.visibility)
  }

  @Test
  fun expiredMalformedAndFutureMessagesCannotDisplayOrPoisonLaterUpdates() {
    val invalid = update("invalid", true)
    AgentNotifications.receive(context, invalid - "updated_at")
    AgentNotifications.receive(context, invalid + ("updated_at" to "invalid"))
    AgentNotifications.receive(
      context,
      invalid + ("updated_at" to (System.currentTimeMillis() - 600001).toString())
    )
    AgentNotifications.receive(
      context,
      invalid + ("updated_at" to (System.currentTimeMillis() + 3600000).toString())
    )
    assertTrue(manager.activeNotifications.isEmpty())
    AgentNotifications.receive(context, update("valid", true))
    assertEquals(2, manager.activeNotifications.size)
  }

  @Test
  fun deniedPermissionDoesNotConsumeAnAlertBeforeTheUserAllowsNotifications() {
    shadowOf(manager).setNotificationsEnabled(false)
    val message = update("attention", true)
    AgentNotifications.receive(context, message)
    assertTrue(manager.activeNotifications.isEmpty())
    shadowOf(manager).setNotificationsEnabled(true)
    AgentNotifications.receive(context, message)
    assertEquals(2, manager.activeNotifications.size)
  }
}
