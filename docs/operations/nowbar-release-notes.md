An unofficial T3 Code Android fork with Samsung Now Bar and Android Live Updates.

This update adds a custom terminal emblem with phase colors and a real progress ring, compact elapsed-time and step summaries, and persistent unread results. Completed work changes to **Ready to review**; errors change to **Needs a look**. Open the completed thread or choose **Dismiss result** to clear it. Read receipts are device-local and tracking starts with this update, so old historical threads are not all treated as unread. Completed-only monitoring releases its wake lock and stops the running timer.

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
