# T3 Code Now Bar

This Android fork targets Samsung phones with current One UI and Android 16+. See [release notes and setup](nowbar-release-notes.md).

## Maintaining updates

The `nowbar` default branch contains the Android feature. The **Now Bar sync and release** workflow merges `pingdotgg/t3code:main` daily, runs notification tests and mobile type checking, builds and tests Android, signs the arm64 APK, and publishes a GitHub release. Upstream merges only advance the branch after a successful build. Conflicts or failed checks leave the previous branch and release available and mark the workflow failed; resolve these before dispatching another release.

Enable GitHub Actions and provide `NOWBAR_KEYSTORE_BASE64` (a PKCS12 keystore with alias `nowbar`) and `NOWBAR_KEYSTORE_PASSWORD`. Keep an offline backup of both: future Android updates must use the same signing identity. Never commit signing material. The workflow run number is the Android version code; preserve the workflow identity and keep version codes increasing if migrating it. GitHub may disable scheduled workflows after prolonged repository inactivity; check Actions if releases stop.

Run the workflow manually with `sync_upstream` to test an upstream merge immediately. To build locally, install the mobile workspace dependencies, set `NOWBAR_VERSION_CODE` and `NOWBAR_VERSION`, prebuild Android, and run `:t3-nowbar:testReleaseUnitTest :app:assembleRelease`. Local debug signing cannot update a published build.

## Device validation

On the target Samsung beta, verify working / approval / question / plan / monitoring states; real plan progress; multiple environments with colliding thread IDs; Next agent and Review deep links; completion demotion; private details on AOD; Unpin remaining dismissed; notification revocation; disconnected network; and background monitoring with the screen off. Install a second signed release over the first and check saved pairing remains. A compile or unit test cannot establish Samsung beta compatibility.

The native notification uses Android's standard styles and promoted ongoing flag, plus Samsung ongoing-activity bundle extras and manifest metadata. Samsung extras are a compatibility adapter, not a promise of an official stable Samsung API. References: [Android Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update), [community Samsung SDK implementation](https://github.com/kirillshsh/nowbar-sdk/blob/main/nowbar/src/main/kotlin/com/nowbar/api/notification/OngoingExtrasBuilder.kt).
