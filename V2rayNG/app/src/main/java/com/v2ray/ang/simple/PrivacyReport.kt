package com.v2ray.ang.simple

/** Allowlist-only report: no arbitrary error, endpoint, device identifier or native output. */
object PrivacyReport {
    fun render(version: String, connected: Boolean, elapsedSeconds: Long, points: Int): String =
        "Sahand VPN $version\nTunnel verified: $connected\nSession elapsed seconds: ${elapsedSeconds.coerceAtLeast(0)}\nSaved healthy points: ${points.coerceAtLeast(0)}\nTraffic bytes: unavailable\nOS lockdown: check Android VPN settings\n"
}
