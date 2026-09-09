An unofficial T3 Code Android fork with Samsung Now Bar and Android Live Updates.

This update fixes the missing Samsung custom layout. Both live monitoring and native push now attach real RemoteViews to Samsung's ongoing-activity component slot: a phase emblem, state badge, plan count, segmented progress and review hint. Android's standard notification content remains available for promotion. The state colors are preserved. Samsung beta rendering still needs a device check.

Open **Settings ? Samsung Now Bar ? Now Bar Lab** to test working, plan progress, approvals, questions, proposed plans, background work, watching, disconnection, unread completion, errors and stopped runs. Compare Samsung custom and standard layouts, adjust plan steps, or load every state and use Next state on the notification. Tests have their own notification ID, never touch real threads, and expire after ten minutes. **Clear test notification** removes them immediately.

Completed real work stays as **Ready to review** until opened or dismissed. Read receipts are device-local.

Google sign-in and live Now Bar monitoring have been confirmed working by the user on the S26 Ultra. Samsung controls the outer card and may ignore individual visual hints; the new appearance still needs device verification. This build includes Firebase for the private misc sender. In Settings, enable Device Notifications and share the generated setup file with the sender administrator to enroll this phone. Ongoing Agent Activity uses the same custom Now Bar states when updates arrive remotely. Delivery still needs verification on the enrolled phone; Android force-stop blocks push until the app is reopened.

- Live task title, plan steps, elapsed time, and attention states across connected environments.
- One promoted card with Open/Review, Next agent, and Unpin actions.
- Samsung status chip, lock-screen text, progress, colors, and tap intent.
- Optional completion alerts and private task details.
- GitHub update checks with checksum validation and Android's signed APK installer.

Install `t3code-nowbar.apk`, pair your environments, then enable **Settings → Samsung Now Bar → Live agent work** and allow Live notifications in Samsung settings. It installs alongside the official app. Pairing must be done again in this separate app.

Monitoring uses the phone's existing connections. Enable it with the app open; Android can still stop the app, especially under battery restrictions. A stale monitor changes to a connection warning and then stops. Open the app to reconnect. Samsung controls final Now Bar placement; One UI beta behavior needs verification on your device.

Automatic update checks offer new APKs; Android asks before installation. This fork does not consume the official app's Expo OTA channel.

The private sender initially watches the T3 environment running on misc. Other environments still use local live monitoring. If Firebase rotates the phone token, use **Share push setup with misc** again. Signing out or disabling Device Notifications stops the native receiver. No Firebase service-account key is included in the APK.
