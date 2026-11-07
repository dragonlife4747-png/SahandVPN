package com.v2ray.ang.simple

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class ConnectionMotionTest {
    @Test fun `connection motion honors system reduction and does not rotate idle`() {
        val s = File("src/main/java/com/v2ray/ang/simple/SimpleActivity.kt").readText()
        assertFalse(s.contains("rememberFrameAngle"))
        assertTrue(s.contains("ValueAnimator.areAnimatorsEnabled()"))
        assertTrue(s.contains("active = busy && motionEnabled"))
        assertTrue(s.contains("stateDescription = title"))
        assertTrue(s.contains("role = Role.Button"))
    }
}
