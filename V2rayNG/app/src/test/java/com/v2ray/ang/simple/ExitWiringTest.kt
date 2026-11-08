package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class ExitWiringTest {
    @Test fun `only verified UI requests exit and every invalid state clears it`() {
        val s = File("src/main/java/com/v2ray/ang/simple/SimpleActivity.kt").readText()
        for (branch in listOf("AppConfig.MSG_STATE_START ->", "AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_STOP_SUCCESS ->", "AppConfig.MSG_STATE_START_FAILURE ->")) {
            assertTrue(s.substringAfter(branch).substringBefore("\n                }").contains("exitLookup.clear()"), branch)
        }
        assertTrue(s.substringAfter("AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS ->").substringBefore("\n                }").contains("exitLookup.verified("))
        assertTrue(s.substringAfter("private fun cancelFlow()").substringBefore("private fun startTest()").contains("exitLookup.clear()"))
        assertTrue(s.substringAfter("override fun onDestroy()").substringBefore("// ---------------------------------------------------------------- data").contains("exitLookup.clear()"))
        assertTrue(s.contains("TextDirection.Ltr"))
        assertTrue(s.contains("R.string.simple_exit_unavailable"))
    }
    @Test fun `reload clears current identity before switching core`() {
        val s = File("src/main/java/com/v2ray/ang/core/CoreServiceManager.kt").readText().substringAfter("private fun reloadCore():")
        assertTrue(s.substringBefore("coreController.stopLoop()").contains("MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START"))
    }
}
