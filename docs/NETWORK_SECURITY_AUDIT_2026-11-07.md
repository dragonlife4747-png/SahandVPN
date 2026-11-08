# Bounded network security audit — 2026-11-07

## Result

**No demonstrated production-code defect was fixed. This is not a DNS/IPv6 leak-free certification.** Android lockdown blocked a separate application UID after the VPN was force-stopped. IPv6 external reachability was unavailable. A working 1.12 tunnel was exercised before the emulator restart, but physical-interface packet observation for that run was inadequate. Subsequent Wi-Fi capture worked, but a healthy 1.12 tunnel could not be re-established after the emulator restored an older snapshot. Therefore connected DNS/IPv4 leak attribution remains **INCONCLUSIVE**, not PASS.

Application source, UI, updater, signing key and version remain unchanged. No release was built or published for this audit.

## Identity and evidence

- Initial repository HEAD: `c682263`; pre-existing untracked `1.11_PLAN.md` and `OLD_COMMIT_DIFFS/` left untouched.
- Initial and final installed package: `com.sahand.vpn`, versionName `1.12`, installed ABI-specific versionCode `4010013`.
- GitHub latest release queried through the real API: `v1.12`, arm64 asset 32,773,641 bytes and universal asset 72,053,170 bytes. This checks metadata, not remote/local byte equality.
- External backup and private observations: `/Users/sahand/.hermes/cache/scratch/vpn-security-20261107-184338/`.
- `repository-backup.tar`, `status.txt`, `release.json`, `baseline-probe.txt`, `connected-probe.txt`, `lockdown-probe.txt`, `all-probes.txt`, `lockdown-on.xml`, `policy-restored.xml`, connectivity dumps, helper source/APK, `wifi-final.pcap`, and `unit-tests-fresh.log` are there. Raw captures may contain third-party server destinations and must not be published.

## Actual application-UID probes

A temporary, independently signed Android activity (`audit.network`) with INTERNET permission executed requests as UID 10222 during the original run, not adb shell UID. It used ordinary default-network APIs, not explicit underlying-network binding. Each phase made a unique system resolver lookup, an independently encoded UDP DNS A request to a public resolver, an IPv4 literal HTTP request, and an IPv6 literal HTTP request. Requests used seven-second timeouts. The helper was uninstalled after testing (`Success`). No VPN credentials/configuration were uploaded.

| Scenario | System DNS / UDP DNS | IPv4 HTTP | IPv6 | Interpretation |
|---|---|---|---|---|
| No VPN, original baseline | unique nonexistent name => UnknownHost; UDP reply received in 9 ms | genuine HTTP 301 in 514 ms | connection failed | Direct IPv4/DNS baseline available; IPv6 unavailable |
| Original 1.12 connected, tun0 present | unique name => UnknownHost; UDP timed out after 7060 ms | genuine HTTP 301 in 979 ms | Host unreachable in 3 ms | IPv4 request succeeds while tunnel exists; DNS availability issue observed, but no physical-interface attribution |
| Both system toggles ON; `am force-stop com.sahand.vpn` | resolver fails immediately; UDP send rejected with EACCES | connect rejected in 9 ms | connect rejected | **PASS for tested UID/scenario:** OS blocks requests without VPN |
| Both toggles restored OFF in Android Settings | first long-lived helper process retained unusable network state; after helper process restart UDP reply in 5 ms | HTTP 301 in 648 ms after helper restart | connection failed | Real baseline restored, not merely persisted setting changed |
| After emulator snapshot restore, no active tunnel | Wi-Fi PCAP contains unique cleartext system/UDP DNS | PCAP contains tagged HTTP GET and response | unavailable | Valid capture positive control; ordinary traffic intentionally direct without VPN |
| Current 1.12 reinstalled, search in progress, no tun0 | plaintext DNS present | tagged HTTP GET present | unavailable | **Not a connected-state leak:** UI dump says Searching; no claim based on misleading probe phase name `v112_connected` |
| Force-stop with lockdown OFF | direct UDP DNS and HTTP succeed | HTTP 301 | unavailable | Expected contrast: recovery/search without Android lockdown is not a kill-switch |

The system-toggle UI dumps show ON/ON during enforcement and OFF/OFF after restoration. No secure-settings writes were used to simulate policy. This is a **force-stop test**, not a measured transient server failure, physical handover or crash-recovery-window test. Manual disconnect warning interaction and physical mobile/Iranian networks were not exercised.

## Capture limitations and emulator restart

`adb emu network capture` initially produced a 150-byte PCAP containing only one IPv6 router advertisement while ordinary Wi-Fi requests clearly succeeded. That capture does **not** observe the Wi-Fi path and cannot substantiate absence of leakage. Host tcpdump failed with BPF Permission denied; sudo was not used.

Restarting the emulator with `-netsim-args '--pcap'` enabled real radiotap Wi-Fi PCAP under the runtime's netsimd artifact directory. That capture contains unique baseline DNS queries and tagged HTTP requests, establishing a valid observation positive control. The emulator unexpectedly restored `default_boot`, including app version 1.8, previous data and no VPN approval. Version 1.12 was reinstalled successfully and VPN app-op consent restored, but repeated bounded automatic searches did not produce a connected 1.12 tunnel. All later samples are classified by actual UI/tunnel state, not helper phase labels. The final installed app is 1.12; system always-on package is null.

## Source review, not substitute for packet proof

- `CoreVpnService.configureNetworkSettings`: IPv4 address plus default `0.0.0.0/0`, or explicit non-LAN ranges when bypass-LAN applies. IPv6 address/routes are conditional. Default DNS is an IPv4 public resolver.
- **Missing IPv6 route alone is not a demonstrated Android bypass.** Authoritative AOSP `VpnService.Builder.allowFamily` documentation says an address family with no address, route or DNS server is blocked by default. Explicit `allowFamily` permits fall-through. Current service does not call `allowFamily` or `allowBypass`. The checked AOSP source is retained privately; source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/net/VpnService.java . Adding an IPv6 DNS address while leaving IPv6 routes disabled would require separate configuration-specific review.
- `configurePerAppProxy` excludes the VPN package itself by default. Other applications remain covered unless per-app split/exclusion settings are selected. App-owned subscription refreshes, endpoint scans, updater and proxy-server bootstrap can therefore be direct by design; they must not be counted as leaked ordinary-app traffic. This is also a trust boundary: not all app-owned HTTP is carried by the tunnel.
- `vpnProtect` delegates to framework `protect`. `NetworkMonitor` supplies underlying networks through `setUnderlyingNetworks`; that setter describes upstream networks rather than binding ordinary apps to them. No process-wide `bindProcessToNetwork` was found in the reviewed application source.
- `CoreConfigManager.configureDns` routes default core DNS via proxy; domain-specific domestic DNS can be direct when routing rules request it. `resolveOutboundDomainsToHosts` may perform platform DNS to bootstrap the server hostname. Private/LAN and per-app split rules are intended exceptions, not evidence of accidental leakage. Effective runtime settings and every custom profile were not extracted from the release app's private data.
- No reliable DoH/DoT destination classification was obtained. No website-based leak test is claimed.

## Regression suite

Executed `:app:testPlaystoreDebugUnitTest --rerun-tasks --offline --console=plain` with real exit code 0, `BUILD SUCCESSFUL in 9s`, all 28 tasks executed. Parsed current Playstore JUnit XML: **148 tests, 0 failures, 0 errors, 0 skipped**. These existing unit/source assertions are not network-leak tests. No RED/GREEN production fix was invented from an inconclusive capture.

## Remaining requirement

Obtain a healthy tunnel while the now-working Wi-Fi capture is running; repeat unique app-UID DNS, IPv4 and actual IPv6-reachable baselines through connected, forced upstream interruption and recovery windows. Correlate direct physical packets against proxy transport/bootstrap exceptions. Repeat lockdown with a live tunnel before interruption and multiple ordinary app UIDs. Until then, report the measured lockdown result separately from the inconclusive connected leak audit.
