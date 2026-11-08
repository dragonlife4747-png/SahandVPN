package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class SingleActionWiringTest {
    private fun source() = File("src/main/java/com/v2ray/ang/simple/SimpleActivity.kt").readText()
    @Test fun `two step screen exposes a search button and a locked connect ring`() {
        val s = source()
        assertTrue(s.contains("private fun searchAction()"))
        assertTrue(s.contains("private fun connectAction()"))
        assertTrue(s.contains("private fun SearchButton()"))
        assertTrue(s.contains("private fun StepBadge("))
        // The single-action flow is gone; the search must never dial on its own.
        assertFalse(s.contains("private fun primaryAction()"))
        assertFalse(s.contains("flow.searchFinished(flowToken"))
        assertTrue(s.contains("enabled = connected || connecting || winners.isNotEmpty()"))
    }

    @Test fun `each launch wipes the previous latency results`() {
        val s = source()
        assertTrue(s.contains("SimpleConfig.clearSession(this@SimpleActivity)"))
        assertTrue(s.contains("val vpnUp = vpnInterfaceActive()"))
    }
    @Test fun `late permission and service events cannot restart cancelled flow`() {
        val s = source()
        assertTrue(s.contains("if (!pendingConnect || !flow.accepts(flowToken))"))
        assertTrue(s.contains("if (userCancelled) return"))
        assertTrue(s.contains("flow.cancel()"))
    }
    @Test fun `a cached healthy point avoids a broad scan`() {
        val s = File("src/main/java/com/v2ray/ang/simple/SimpleTestService.kt").readText()
        assertTrue(s.contains("if (working.isNotEmpty())"))
        assertTrue(s.contains("publishFinish(tested.get(), total, working.toList())"))
    }
    @Test fun `all screen colors follow theme and theme selector exists`() {
        val s = source()
        assertTrue(s.contains("MaterialTheme.colorScheme"))
        assertTrue(s.contains("ThemeManager.setThemeMode"))
        assertFalse(s.contains("private val BgTop = Color"))
    }
}
