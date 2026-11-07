# Android system kill switch — Sahand VPN 1.9

Tap the shield in the simple screen. The Persian/English dialog opens the real Android VPN settings list (general Settings fallback), not an undocumented deep link. Connect once, select the gear beside Sahand VPN, then explicitly enable **Always-on VPN** and **Block connections without VPN**. The application never writes either policy setting. Always-on alone is not blocking.

## Contract and caveats

Android enforces lockdown for covered user traffic while this VPN is unavailable. Disconnecting manually, losing all saved working profiles, or losing server connectivity may leave internet blocked. Reconnect, or manually disable blocking in Android settings to regain direct internet/search for new points.

This is not a guarantee beyond Android's VPN contract. The VPN application needs outside-tunnel access to its endpoint. OS/OEM exemptions, tethering, direct/bypass routing inside a live VPN and per-app exclusions require care. Excluded apps can be blocked rather than protected. Neither this UI nor a running core proves that remote traffic is private or healthy.

The dialog reads `VpnService.isAlwaysOn` and `isLockdownEnabled` from the daemon via a non-exported, permission-protected service Messenger on API 29+. It displays each value separately, not an app preference. If the service is stopped, the reply is unavailable, or the OS is older, status is explicitly unknown; consult Android settings. Reading status does not start a service or ask for VPN authorization.

The manifest already advertised SUPPORTS_ALWAYS_ON=true. A system start (null intent or android.net.VpnService) now recovers a valid persisted selected profile or a valid last-search winner, without needing UI intent extras. Repeated starts do not replace an active tunnel. No new server or permissions. Boot handling remains credential-unlock dependent: this release does not claim pre-unlock connectivity. OS always-on launch is independent of the optional app start-on-boot preference. App disconnect stops app recovery; OS policy may still restart it, and lockdown stays the user's OS choice.

## Verification (API 36 Pixel8 emulator)

- Release assemble + full JVM suite exited 0, 132 tests, zero failures/errors. Existing reliability/updater tests preserved.
- Signed arm64 installed with `Success`; UI hierarchy exposed `System kill switch`, instructions, separate unknown values while stopped, and `Open Android VPN settings`. Button navigated to the actual VPN settings list.
- Both OS toggles were enabled through Android Settings UI; the blocking confirmation was explicitly accepted in the test emulator. `dumpsys vpn_management` recorded `lockdown=true alwaysOn=true`.
- Forced package stop interrupted the tunnel; the dump reported active VPN type -1. A direct shell TCP request to www.gstatic.com:80 failed with `nc: connect: Permission denied`. This is actual OS enforcement evidence for the tested shell UID, not a packet-capture audit of all apps.
- Both toggles were subsequently disabled through the UI, the package stopped, and the identical direct HTTP request returned `HTTP/1.0 204 No Content`. Final settings: always_on_vpn_app=null, always_on_vpn_lockdown=0. Earlier test-only `settings` writes did not clear in-memory policy; UI restoration was required and verified.
- Existing saved server started a TUN via the OS always-on launch, but the current server failed health checks and no healthy winner was available. **Successful normal proxy/tunnel traffic was NOT verified in this run.** Do not describe this as a full healthy-tunnel/recovery test. No new test server was added.

Logs/hierarchies are retained locally under ~/.hermes/cache/scratch (`final19-build.log`, `dialog19.xml`, `gear19.xml`, `vpn-final-blocked19.txt`). No claim of a clean-clone build, physical-device enforcement, DNS/IPv6 leak audit, or working remote proxy is made.
