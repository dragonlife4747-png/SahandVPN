package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class SearchLifecycleWiringTest {
    private fun source(name: String) = File("src/main/java/com/v2ray/ang/simple/$name.kt").readText()
    @Test fun `all search exits go through cleanup before outcome becomes visible`() {
        val s = source("SimpleTestService")
        assertTrue(s.contains("finally {\n                resources.close()"), "runner must own unconditional resource cleanup")
        assertTrue(s.contains("completeAfterShutdown()"), "outcome must be committed only after shutdown")
        assertTrue(s.contains("outcome = {"))
    }
}
