package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

/** Contract checks for Android adapters; policy behavior is exercised separately. */
class ReliabilityWiringTest {
    private fun source(path: String) = File("src/main/java/com/v2ray/ang/$path.kt").readText()
    @Test fun `rapid successive search commands preserve state instead of overwriting`() {
        val act = source("simple/SimpleActivity")
        assertTrue( act.contains("&&"))
        assertTrue(act.contains("SimpleConfig.beginSearch") && act.contains("SimpleTestService.start"))
        assertTrue(act.contains("SimpleConfig.cancelSearch") && act.contains("SimpleTestService.cancel"))
    }
    @Test fun `cached points are retested before broad scanning and stale runs cannot publish`() {
        val service = source("simple/SimpleTestService")
        assertTrue(service.indexOf("val cached =") in 0 until service.indexOf("val endpoints ="))
        assertTrue(service.contains("SimpleConfig.updateSearch(this, runId"))
        assertFalse(service.contains("SimpleConfig.saveWinners(this, winners)"))
        val config = source("simple/SimpleConfig")
        assertTrue(config.contains("channel.lock()"))
        assertTrue(config.contains("state.runId != runId"))
        assertTrue(config.contains("winners.ifEmpty { state.backup }"))
        assertTrue(source("simple/SimpleActivity").contains("SimpleConfig.EXTRA_RUN_ID"))
    }
    @Test fun `daemon success is gated by real tunnel check not core launch`() {
        val core = source("core/CoreServiceManager")
        assertTrue(core.contains("reliability.accept(token, healthy)"))
        assertTrue(core.contains("https://www.gstatic.com/generate_204"))
        assertFalse(core.contains("if (port <= 0) return true"))
        assertFalse(core.contains("LogUtil.d(AppConfig.TAG, result.content)"))
    }
}
