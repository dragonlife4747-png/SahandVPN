# 1.13 scanner lifecycle verification

## Reproduced root cause

1.12 cached-retest success returned directly from `runTest`, without `finishService`.
The emulator had both SimpleTestService foreground ID 12 (`core_test_channel`) and
CoreVpnService foreground ID 1 (`CORE_M_CH_ID_V2`). These are distinct IDs/channels,
not the Android system VPN icon. Cancellation only flipped a flag; TCP sockets,
probe HTTP calls/executor tasks and native controllers were not owned by the service.

## Fix

- A per-request SearchResources owner closes TCP sockets, cancels HTTP calls, stops
  native controllers, interrupts executors and waits for probe workers to terminate.
- Runner finally cleanup covers cached success, broad success, empty lists, errors,
  cancellation and a three-minute deadline. Foreground removal precedes publishing
  the completed outcome; no persisted done outcome is visible during active probes.
- Activity does not connect from a stalled search's partial results; it cancels instead.
- Only an active explicit single-action flow may start a search, never connected state.
  Bootstrap remains read-only; existing current-VPN health/recovery is unchanged.
- No composable, icon, theme, language, updater or kill-switch changes.

## Real execution

- Before: two foreground services observed after cached success.
- Full `testPlaystoreDebugUnitTest`: 152 tests, zero failures/errors/skips.
- Regression wiring test failed before the fix; resource tests initially failed to
  compile because the new owner did not yet exist. Three resource behavioral tests
  exercise real sockets, worker interruption/termination, late registration and
  exception-tolerant idempotent cleanup.
- `assemblePlaystoreRelease`: exit 0, BUILD SUCCESSFUL; final APK installed with
  `adb install -r`, Success, preserving app data/signing identity.
- Final release: scanner present during explicit request; afterward only VPN service.
  Exactly one app notification (VPN id 1), no SimpleTestRunner or pool executor threads
  in the tasks process. Cached connection succeeded; actual HTTPS via device SOCKS
  tunnel returned 204 (`adb forward` + host curl). Forward removed afterward.
- Explicit disconnect: no services. New search, cancel, wait 30 seconds: no services,
  no scanner runner/executor threads. Runtime/Go/OkHttp idle threads can remain in the
  cached tasks process; absence of a process is not required for stopped scanning.
- Device-specific error/empty/three-minute timeout paths were not separately forced;
  shared finally/resource cleanup and JVM tests cover the mechanism, not full Android
  instrumentation of every path. No security/leak audit claim is made here.

## Artifact

`dist/SahandVPN_1.13_arm64-v8a.apk` — 32,776,213 bytes.
SHA-256: `243901a99c16e9dff2c82b502baa4de7a28e4207484b12d0e8a275dfd9c79b4c`
VersionName 1.13, base versionCode 10014, actual Playstore split code 4010014
(existing flavor output scheme retained).
Same 1.12 signer certificate SHA-256:
`008e348bd83f51ff067be9ed14fcba6ea526e000999790292303bf32bd91cd02`.

Universal APK from the same release build: 72,055,742 bytes.
SHA-256: `4fd0f71efd213e54473906f11cc130d9d131deaa00c8b0c9b850f9f651669a94`.
Its package/version and signing certificate match the arm64 APK. Device runtime
verification above used arm64; the universal APK was not separately device-tested.

Raw device logs and notification dumps are private and excluded from source publication.
