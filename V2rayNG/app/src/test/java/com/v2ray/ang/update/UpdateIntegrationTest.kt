package com.v2ray.ang.update

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class UpdateIntegrationTest {
    private val main = File("src/main")
    @Test fun simpleEntryUsesRetainedUpdaterAndSecureProvider() {
        val entry = File(main, "java/com/v2ray/ang/simple/SimpleActivity.kt").readText()
        assertTrue(entry.contains("UpdateViewModel"))
        assertTrue(entry.contains("UpdateDialog"))
        val manifest = File(main, "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertTrue(manifest.contains("android:name=\".update.UpdateFileProvider\""))
        assertTrue(manifest.contains("\${applicationId}.updates"))
    }
}
