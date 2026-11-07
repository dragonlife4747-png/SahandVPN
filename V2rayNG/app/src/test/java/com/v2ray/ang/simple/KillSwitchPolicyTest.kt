package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KillSwitchPolicyTest {
    @Test fun `unknown system status is not off or protected`() {
        assertNull(KillSwitchPolicy.protected(null, null))
        assertNull(KillSwitchPolicy.protected(true, null))
        assertFalse(KillSwitchPolicy.protected(true, false)!!)
        assertFalse(KillSwitchPolicy.protected(false, false)!!)
        assertTrue(KillSwitchPolicy.protected(true, true)!!)
    }
    @Test fun `recover uses selected valid profile then a valid saved winner only`() {
        val valid = setOf("selected", "winner")
        assertEquals("selected", KillSwitchPolicy.recover("selected", listOf("winner"), valid::contains))
        assertEquals("winner", KillSwitchPolicy.recover("deleted", listOf("deleted", "winner"), valid::contains))
        assertNull(KillSwitchPolicy.recover(null, emptyList(), valid::contains))
        assertNull(KillSwitchPolicy.recover("deleted", listOf("deleted"), valid::contains))
    }
}
