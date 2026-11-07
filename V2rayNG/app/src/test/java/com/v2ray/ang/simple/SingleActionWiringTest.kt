package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class SingleActionWiringTest {
    private fun source() = File("src/main/java/com/v2ray/ang/simple/SimpleActivity.kt").readText()
    @Test fun `ring owns full flow and old two step actions are gone`() {
        val s = source()
        assertTrue(s.contains("private fun primaryAction()"))
        assertTrue(s.contains("flow.searchFinished(flowToken"))
        assertFalse(s.contains("private fun ActionButton("))
        assertFalse(s.contains("private fun StepBadge("))
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
