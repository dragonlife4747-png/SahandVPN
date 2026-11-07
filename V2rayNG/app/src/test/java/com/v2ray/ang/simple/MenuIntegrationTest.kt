package com.v2ray.ang.simple

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class MenuIntegrationTest {
    @Test fun `home exposes only four accessible secondary icons at the requested edges`() {
        val source = File("src/main/java/com/v2ray/ang/simple/SimpleActivity.kt").readText()
        assertFalse(source.contains("DropdownMenu"))
        assertFalse(source.contains("showDetails"))
        assertFalse(source.contains("shareDiagnostics"))
        val header = source.substringAfter("private fun Header()").substringBefore("private fun LanguageGlyph")
        assertTrue(header.contains("IconAction(R.string.simple_theme"))
        assertTrue(header.contains("IconAction(R.string.simple_language"))
        val footer = source.substringAfter("private fun Footer()").substringBefore("private fun IconAction")
        assertTrue(footer.contains("IconAction(R.string.simple_update_check"))
        assertTrue(footer.contains("IconAction(R.string.kill_switch_title"))
        assertTrue(source.contains("contentDescription = str(label)"))
        assertTrue(source.contains("Modifier.size(48.dp)"))
    }
}
