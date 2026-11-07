# Sahand VPN 1.9

- Real Android system kill-switch integration: tap the shield for Persian/English instructions and Android VPN settings.
- User must explicitly enable BOTH Always-on VPN and Block connections without VPN. No app-only toggle and no automatic policy change.
- Reads actual API 29+ service status; unavailable/stopped service means unknown, not off.
- OS launches recover valid persisted profiles; repeated starts preserve the active tunnel. Existing 1.7 reliability and 1.8 updater retained.
- Same signing certificate and package; base versionCode 10010, APK versionCode 4010010.

**Security:** manual disconnect can leave internet blocked. Excluded apps, OS exemptions, direct/bypass routes and tethering are not a universal privacy guarantee. Read docs/KILL_SWITCH.md.

**Verified:** 132 JVM tests pass; signed release build exits 0; API36 installation Success; real OS toggles enabled through Settings; after force-stop a direct TCP connection was denied by the OS, and the same HTTP request returned 204 after restoring both toggles off. **Normal successful remote proxy traffic could not be verified because the existing saved server failed health checks; this is not a full healthy-tunnel test.** No new server added.

SHA-256:
- arm64-v8a: `5eb7b356846616b30e7025b120574a44d994ae33e64567446638bdc64d94cc72`
- universal: `9b85cbf2312dc0fa029b6331b122f78584e3c004a0661e763177d354d41c8663`
