package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class KillSwitchTest {
    private fun source(name: String) = File("src/main/java/com/v2ray/ang/$name.kt").readText()
    @Test fun `system launches recover a saved profile and do not rebuild an active tunnel`() {
        val service = source("service/CoreVpnService")
        assertTrue(service.contains("recoverSavedProfile()"))
        assertTrue(service.contains("isRunning") && service.contains("START_STICKY"))
        assertTrue(service.contains("SimpleConfig.loadWinners"))
        assertTrue(service.contains("decodeServerConfig"))
    }
    @Test fun `settings entry reads OS state and never enables policy itself`() {
        val ui = source("simple/SimpleActivity")
        assertTrue(ui.contains("ACTION_VPN_SETTINGS"), "Missing real system settings entry")
        assertTrue(ui.contains("kill_switch_title"))
        assertTrue(ui.contains("String.format"))
        assertFalse(ui.contains("putSecure"))
        val service = source("service/CoreVpnService")
        assertTrue(service.contains("isLockdownEnabled"))
        assertTrue(service.contains("isAlwaysOn"))
        assertTrue(service.contains("com.sahand.vpn.SYSTEM_STATUS"))
    }
}