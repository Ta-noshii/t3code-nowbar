An unofficial T3 Code Android fork with Samsung Now Bar and Android Live Updates.

This update restores the public configuration needed to display T3 Account and T3 Connect, includes upstream's latest Android notification source, and derives its version from the upstream mobile version. The earlier `1.0.5` release incorrectly omitted cloud configuration.

**Google sign-in is not verified:** the fork has a separate Android package/signing certificate, which requires registration with T3's identity provider. Restoring the account screen alone does not resolve that requirement. Remote push also remains unavailable without Firebase registration for the fork; the Now Bar feature below uses active app connections.

- Live task title, plan steps, elapsed time, and attention states across connected environments.
- One promoted card with Open/Review, Next agent, and Unpin actions.
- Samsung status chip, lock-screen text, progress, colors, and tap intent.
- Optional completion alerts and private task details.
- GitHub update checks with checksum validation and Android's signed APK installer.

Install `t3code-nowbar.apk`, pair your environments, then enable **Settings → Samsung Now Bar → Live agent work** and allow Live notifications in Samsung settings. It installs alongside the official app. Pairing must be done again in this separate app.

Monitoring uses the phone's existing connections. Enable it with the app open; Android can still stop the app, especially under battery restrictions. A stale monitor changes to a connection warning and then stops. Open the app to reconnect. Samsung controls final Now Bar placement; One UI beta behavior needs verification on your device.

Automatic update checks offer new APKs; Android asks before installation. This fork does not consume the official app's Expo OTA channel.
