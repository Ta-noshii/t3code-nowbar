# T3 Code Now Bar

This Android fork targets Samsung phones with current One UI and Android 16+. See [release notes and setup](nowbar-release-notes.md).

## Maintaining updates

The `nowbar` default branch contains the Android feature. The **Now Bar sync and release** workflow merges `pingdotgg/t3code:main` daily, runs notification tests and mobile type checking, builds and tests Android, signs the arm64 APK, and publishes a GitHub release. Upstream merges only advance the branch after a successful build. Conflicts or failed checks leave the previous branch and release available and mark the workflow failed; resolve these before dispatching another release.

Enable GitHub Actions and provide `NOWBAR_KEYSTORE_BASE64` (a PKCS12 keystore with alias `nowbar`) and `NOWBAR_KEYSTORE_PASSWORD`. Keep an offline backup of both: future Android updates must use the same signing identity. Never commit signing material. The workflow run number is the Android version code; preserve the workflow identity and keep version codes increasing if migrating it. GitHub may disable scheduled workflows after prolonged repository inactivity; check Actions if releases stop.

Run the workflow manually with `sync_upstream` to test an upstream merge immediately. To build locally, install the mobile workspace dependencies, copy the upstream `.env.example` to `.env` for public T3 Connect configuration, set `NOWBAR_VERSION_CODE`, prebuild Android, and run `:t3-nowbar:testReleaseUnitTest :app:assembleRelease`. The display version derives from upstream's mobile version plus `-nowbar.<build number>`. Local debug signing cannot update a published build.

Release builds require the public Clerk publishable key, JWT template, and relay URL from upstream `.env.example`. Without these settings upstream intentionally hides account and Connect screens. The release checks validate the generated Expo config as well as the config plugin.

Google/T3 Connect sign-in has been verified by the user in this signed fork. Keep the public upstream cloud settings intact across merges.

## Private Android push

The release requires `NOWBAR_GOOGLE_SERVICES_JSON` for `com.tanoshii.t3code.nowbar`. `NOWBAR_PUSH_TRANSPORT=host` enables private sender enrollment and prevents registering this Firebase token against the official relay's different Firebase project. Keep the service-account key only on the sender, with FCM sender permissions.

Enable **Settings ? Device Notifications**, then share the setup JSON to the sender administrator. Install it as `~/.config/t3-nowbar-push/device.json` with mode 600 alongside the service-account and paired read-only connection JSON. Run `infra/relay/scripts/android-push-watch.ts` with those three paths using Node 24 and the relay workspace dependencies. The misc systemd user service restarts failures; the live T3 service does not need restarting. Re-export enrollment after a device-token change. This setup watches one paired environment; additional environments need their own sender connection.

Validate delivery after backgrounding the signed app: working, approval, input, plan, unread result, read, dismiss, notification permission, and sign-out. Native receipt validates device/account identity and freshness before rendering. Active cards have a renewed five-minute lease; unread results remain until read/dismissed. Android force-stop prevents FCM receipt until the user opens the app. A successful FCM API response proves acceptance, not device display.

## Device validation

On the target Samsung beta, verify working / approval / question / plan / monitoring states; real plan progress; multiple environments with colliding thread IDs; Next agent and Review deep links; completion demotion; private details on AOD; Unpin remaining dismissed; notification revocation; disconnected network; and background monitoring with the screen off. Install a second signed release over the first and check saved pairing remains. A compile or unit test cannot establish Samsung beta compatibility.

The native notification uses Android's standard styles and promoted ongoing flag, plus Samsung ongoing-activity bundle extras and manifest metadata. Samsung extras are a compatibility adapter, not a promise of an official stable Samsung API. References: [Android Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update), [community Samsung SDK implementation](https://github.com/kirillshsh/nowbar-sdk/blob/main/nowbar/src/main/kotlin/com/nowbar/api/notification/OngoingExtrasBuilder.kt).
