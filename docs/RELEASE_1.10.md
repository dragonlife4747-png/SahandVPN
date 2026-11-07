# Sahand VPN 1.10

- One central ring: start automatic cached-point retesting → search only if needed → connect. Tap busy ring to cancel the entire request; connected tap opens a disconnect confirmation.
- Removed two bottom actions/step badges. Point count, language and Android kill-switch guidance live under Details.
- Persistent system/day/night appearance control. Existing Vazirmatn typography, RTL, safe insets and scrollable large-font content retained. All dialogs use theme colors.
- No idle animation loop; busy ring uses frame feedback even with animation scale zero.
- Android Always-on / Block connections without VPN remains an OS setting, not an app toggle. Disconnect confirmation explicitly warns that lockdown may keep internet blocked, including when status is unknown.
- Existing signed GitHub updater retained; numeric version comparison tests cover 1.10 > 1.9.

## Verification

`JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home` and `ANDROID_HOME=$HOME/Library/Android/sdk` exported; `./gradlew :app:testPlaystoreDebugUnitTest :app:assemblePlaystoreRelease --console=plain` exited 0. 142 JVM tests, 0 failures/errors/skips. Includes policy behavior and source-wiring guard tests (not Android instrumentation tests). Pre-implementation wiring tests and cached short-circuit test were observed failing first.

Signed arm64 release installed over existing app on Pixel 8 API 36 emulator. Actual subscription search found five HTTPS-healthy points and automatically established tun0; screen showed Connected only after the existing SOCKS 204 verification path. Final APK cached-retest path also automatically connected. Cancelled search remained idle after 30 seconds; manual disconnect left no running app services after 15 seconds. Offline tap showed an internet error without starting search. Day/night, persisted night after process restart, Persian RTL and 1.5x font were exercised; screenshots visually inspected for clipping/readability. Busy/cancel text remains visible with animator scale 0.

Limits: emulator verification, not a physical-phone or all-app leak audit. Existing 1.9 OS-lockdown enforcement tests are not repeated by this UI release. Endpoint availability varies. Existing updater install pipeline retained, not re-exercised end-to-end for this release. Build retains upstream Gradle/Kotlin deprecation warnings.

Base versionCode 10011; Playstore APK versionCode 4010011. Signing certificate SHA-256 matches 1.9: `008e348bd83f51ff067be9ed14fcba6ea526e000999790292303bf32bd91cd02`.
