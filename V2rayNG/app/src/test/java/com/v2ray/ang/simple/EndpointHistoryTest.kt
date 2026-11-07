package com.v2ray.ang.simple

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class EndpointHistoryTest {
    @Test fun `failed endpoint survives reload and cools down then retries`() {
        val file = File.createTempFile("history", ".json").apply { deleteOnExit() }
        val history = EndpointHistory(file)
        history.record("private.example:443", false, 1000)
        assertFalse(EndpointHistory(file).eligible("private.example:443", 1001))
        assertTrue(EndpointHistory(file).eligible("private.example:443", 61001))
        assertFalse(file.readText().contains("private.example"))
    }
}
