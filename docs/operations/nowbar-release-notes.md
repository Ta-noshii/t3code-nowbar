An unofficial T3 Code Android fork with Samsung Now Bar and Android Live Updates.

This update removes repeated state text from custom Samsung cards. The custom badge shows the state; the lower row shows project and elapsed time. The full custom layouts, logos, counts and progress remain available. Lab cards use an example task title rather than repeating the selected state.

Model names now use the same environment/provider catalog as the model picker, for local monitoring and host push. Lab samples also use catalog model names instead of hard-coded model IDs. If a model is absent from the catalog, live cards retain its original ID rather than guessing a name.

The original cutoff in one Samsung lock-screen notification view remains unresolved. These text changes do not claim to fix it. Native tests cover the custom/standard summaries, model labels and retained custom content; actual Samsung rendering still needs confirmation on the phone.

Approval requests, questions and proposed plans can now nudge once when attention is needed. Finished, failed and stopped work can nudge once and stays in Now Bar until opened or dismissed. Local monitoring and host push share alert history, so switching delivery paths does not repeat an alert. Old cached completions appear silently. **Attention nudges** and **Completion notifications** control alerts separately; Android channel settings and Do Not Disturb still apply.

Open **Settings → Samsung Now Bar → Now Bar Lab** to test working, plan progress, approvals, questions, proposed plans, background work, watching, disconnection, unread completion, errors and stopped runs. Compare custom and standard layouts, inspect the expanded preview, switch OpenAI/Claude branding, adjust progress and nudge the selected state. Tests never run an agent or change unread threads. **Clear test notification** removes them; otherwise they expire after ten minutes. The expanded preview forces the larger layout for inspection and does not prove Samsung selects it during normal expansion.

Install `t3code-nowbar.apk` over the existing fork to retain settings and pairing. Google sign-in, host Firebase delivery and Samsung custom RemoteViews were confirmed working on the S26 Ultra in the previous release. This release uses the same package and signing key. Enable **Settings → Samsung Now Bar → Live agent work** and allow Live notifications in Samsung settings.

The private sender watches the T3 environment on misc; other environments use local live monitoring. If Firebase rotates the phone token, use **Share push setup with misc** again. Signing out or disabling Device Notifications stops the native receiver. Android force-stop blocks push until the app is reopened. No Firebase service-account key is included in the APK.

Automatic update checks offer new APKs with checksum validation; Android asks before installation. This fork does not consume the official app’s Expo OTA channel. Both local builds and scheduled upstream builds advance the same release version sequence.
