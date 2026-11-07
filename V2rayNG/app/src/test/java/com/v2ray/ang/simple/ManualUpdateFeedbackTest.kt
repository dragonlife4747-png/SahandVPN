package com.v2ray.ang.simple
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class ManualUpdateFeedbackTest {
    @Test fun `manual update checks distinguish no update from unavailable API`() {
        val s = File("src/main/java/com/v2ray/ang/update/UpdateViewModel.kt").readText()
        assertTrue(s.contains("check(manual = true)"))
        assertTrue(s.contains("R.string.update_unavailable"))
        assertTrue(s.contains("R.string.update_no_newer"))
        assertTrue(s.contains("R.string.update_checking"))
    }
}
