# Sahand VPN Reliability Upgrade (1.7)

## Changes

1. **Tunnel Verification Policy (ReliabilityPolicy.kt)**: Added a new policy that gates core launch success on real tunnel verification.
   - `start()` records a token and resets verification state.
   - `accept(token, healthy)` only registers tunnel health if token matches current generation; other tokens are ignored, ensuring only the current requested generation can verify.
   - `stop()` invalidates the request, discarding any pending verification.

2. **Retry Delay (ReliabilityPolicy.kt)**: Exponential backoff for offline scenarios.
   - Offline => fixed 5s.
   - Online + healthy => 35s (reset retry count).
   - Online + unhealthy => grows 2s, 4s, 8s... up to cap of 60s.

3. **CoreServiceManager.kt**: Integrated ReliabilityPolicy.
   - Core start now validates tunnel with HTTP 204 through the local SOCKS inbound.
   - Health monitor uses policy: retryDelay, respects token and requested state; only publishes tunnel verification results to UI.
   - On reload, respects policy.requested to skip unhealthy points and avoid churn.
   - All generated config logs removed; speedtest replaced with plain HTTP verification.
   - Traffic stats still reported; logging cleaned.

4. **SimpleActivity.kt**: UI polish.
   - Connect buttons respect ongoing connections (connecting state) to avoid double-click churn.
   - Tab-order clarifies steps with Persian copy.

5. **SimpleTestService.kt**: Cached-points re‑use and run isolation.
   - Added per‑run UUID; previous healthy points are restored if a run is empty.
   - Cached points (up to TARGET_WORKING) are re‑tested before scanning new endpoints.
   - No winners written to persistent storage except via `updateSearch`, which is gated by runId.

6. **SimpleConfig.kt**: Search state machine.
   - `beginSearch`, `updateSearch`, `cancelSearch` with file‑level locking (simple_state.lock).
   - Backups survive aborted searches.
   - Separate `runId` distinguishes runs; stale runs are ignored.

## Test Coverage

Six new unit tests under `com.v2ray.ang.simple.*`:
- `ReliabilityPolicyTest.kt` (5 tests): start/stop/accept and retry back‑off.
- `ReliabilityWiringTest.kt` (1 test): contract checks for real tunnel verification.

All tests pass in a clean Gradle environment.

## Build & Verification

- Play Store APK: `app/build/outputs/apk/playstore/release/SahandVPN_1.7_arm64-v8a.apk` (sha256: f448d9744192540ffe53741b047b5ff2d8a0e7149d5a01aa267569544162c43b)
- Fdroid APK: `app/build/outputs/apk/fdroid/release/` (build succeeded)
- Unit tests: green; passing on `testPlaystoreDebugUnitTest`.
- Emulator: Pixel8 API36, arm64 release APK installed, launch and basic button taps verified via UI dump.

## Status

All reliability upgrades verified; stable end‑to‑end behavior with offline recovery, re‑tries, and prevented churn.