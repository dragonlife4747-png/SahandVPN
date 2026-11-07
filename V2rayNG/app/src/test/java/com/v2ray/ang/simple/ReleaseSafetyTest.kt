package com.v2ray.ang.simple

import com.google.gson.JsonParser
import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReleaseSafetyTest {
    @Test fun `automatic profiles reject insecure TLS and plaintext VLESS`() {
        fun safe(json: String) = OutboundSafety.accepts(JsonParser.parseString(json).asJsonObject)
        assertFalse(safe("""{"protocol":"vless","streamSettings":{"security":"none"}}"""))
        assertFalse(safe("""{"protocol":"trojan","streamSettings":{"security":"tls","tlsSettings":{"allowInsecure":true}}}"""))
        assertTrue(safe("""{"protocol":"trojan","streamSettings":{"security":"tls"}}"""))
    }
    @Test fun `details and diagnostics share are removed from the main screen`() {
        val activity = File("src/main/java/com/v2ray/ang/simple/SimpleActivity.kt").readText()
        assertFalse(activity.contains("DropdownMenu"))
        assertFalse(activity.contains("shareDiagnostics"))
        assertFalse(activity.contains("showDetails"))
        assertFalse(activity.contains("ACTION_SEND"))
        assertTrue(activity.contains("Footer()"))
        assertTrue(activity.contains("IconAction(R.string.simple_update_check"))
        assertTrue(activity.contains("IconAction(R.string.kill_switch_title"))
        assertTrue(activity.contains("contentDescription = str(label)"))
        assertTrue(activity.contains("Modifier.size(48.dp)"))
    }
}