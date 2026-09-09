An unofficial T3 Code Android fork with Samsung Now Bar and Android Live Updates.

This update restores the full custom Now Bar layout from .11. The .12 single-line fallback caused Samsung to omit custom status, counts and progress in views that had previously worked. The full compact layout is the default again, with the expanded layout, provider logos and progress preserved.

This is a regression recovery. The original clipping in one Samsung lock-screen notification view remains unresolved; this release does not claim to fix it. Native tests now require the default view itself to retain custom status, counts and progress.

Approval requests, questions and proposed plans can now nudge once when attention is needed. Finished, failed and stopped work can nudge once and stays in Now Bar until opened or dismissed. Local monitoring and host push share alert history, so switching delivery paths does not repeat an alert. Old cached completions appear silently. **Attention nudges** and **Completion notifications** control alerts separately; Android channel settings and Do Not Disturb still apply.

Open **Settings → Samsung Now Bar → Now Bar Lab** to test working, plan progress, approvals, questions, proposed plans, background work, watching, disconnection, unread completion, errors and stopped runs. Compare custom and standard layouts, inspect the expanded preview, switch OpenAI/Claude branding, adjust progress and nudge the selected state. Tests never run an agent or change unread threads. **Clear test notification** removes them; otherwise they expire after ten minutes. The expanded preview forces the larger layout for inspection and does not prove Samsung selects it during normal expansion.

Install `t3code-nowbar.apk` over the existing fork to retain settings and pairing. Google sign-in, host Firebase delivery and Samsung custom RemoteViews were confirmed working on the S26 Ultra in the previous release. This release uses the same package and signing key. Enable **Settings → Samsung Now Bar → Live agent work** and allow Live notifications in Samsung settings.

The private sender watches the T3 environment on misc; other environments use local live monitoring. If Firebase rotates the phone token, use **Share push setup with misc** again. Signing out or disabling Device Notifications stops the native receiver. Android force-stop blocks push until the app is reopened. No Firebase service-account key is included in the APK.

Automatic update checks offer new APKs with checksum validation; Android asks before installation. This fork does not consume the official app’s Expo OTA channel. Both local builds and scheduled upstream builds advance the same release version sequence.
