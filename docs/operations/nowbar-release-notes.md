An unofficial T3 Code Android fork with Samsung Now Bar and Android Live Updates.

This release restores upstream syncing after the failed September 10–19 runs and updates the Android app to the upstream 1.2.1 base. It preserves Samsung custom layouts, expanded cards, agent-written status updates, model/provider logos, unread results and supported notification nudges.

Now Bar controls remain under **Settings → Samsung Now Bar**. Private host push setup is under **Settings → Notifications**, alongside upstream's reorganized notification settings. Enable **Samsung custom components** and **Expanded layout** to show the larger live card. Turning Expanded layout off returns to automatic layout selection.

The original cutoff in one Samsung lock-screen notification view remains unresolved; this release repairs sync/build failures and does not claim to fix that separate layout issue.

Approval requests, questions, connection loss and terminal states take precedence. Updates are limited to the three visible working cards and do not publish for every streamed token. Hide task details still conceals status and model names.

The original cutoff in one Samsung lock-screen notification view remains unresolved. The expanded layout can be selected explicitly; it does not force Samsung to resize its container. Native tests verify layout switching and status text, while actual Samsung rendering needs phone confirmation.

Approval requests, questions and proposed plans can now nudge once when attention is needed. Finished, failed and stopped work can nudge once and stays in Now Bar until opened or dismissed. Local monitoring and host push share alert history, so switching delivery paths does not repeat an alert. Old cached completions appear silently. **Attention nudges** and **Completion notifications** control alerts separately; Android channel settings and Do Not Disturb still apply.

Open **Settings → Samsung Now Bar → Now Bar Lab** to test working, plan progress, approvals, questions, proposed plans, background work, watching, disconnection, unread completion, errors and stopped runs. Compare custom and standard layouts, inspect the expanded preview, switch OpenAI/Claude branding, adjust progress and nudge the selected state. Tests never run an agent or change unread threads. **Clear test notification** removes them; otherwise they expire after ten minutes. The expanded preview forces the larger layout for inspection and does not prove Samsung selects it during normal expansion.

Install `t3code-nowbar.apk` over the existing fork to retain settings and pairing. Google sign-in, host Firebase delivery and Samsung custom RemoteViews were confirmed working on the S26 Ultra in the previous release. This release uses the same package and signing key. Enable **Settings → Samsung Now Bar → Live agent work** and allow Live notifications in Samsung settings.

The private sender watches the T3 environment on misc; other environments use local live monitoring. If Firebase rotates the phone token, use **Share push setup with misc** again. Signing out or disabling Device Notifications stops the native receiver. Android force-stop blocks push until the app is reopened. No Firebase service-account key is included in the APK.

Automatic update checks offer new APKs with checksum validation; Android asks before installation. This fork does not consume the official app’s Expo OTA channel. Both local builds and scheduled upstream builds advance the same release version sequence.
