package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReliabilityPolicyTest {
    @Test fun `only current requested generation can verify tunnel`() {
        val p = ReliabilityPolicy()
        val start = p.javaClass.getMethod("start").invoke(p) as Long
        val result = p.javaClass.getMethod("accept", Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        assertEquals(true, result.invoke(p, start, true))
        assertTrue(p.verified)
        p.javaClass.getMethod("stop").invoke(p)
        assertEquals(false, result.invoke(p, start, true))
        assertFalse(p.verified)
    }
    @Test fun `offline never consumes retry budget and retries back off to a cap`() {
        val p = ReliabilityPolicy()
        p.start()
        val retry = p.javaClass.getMethod("retryDelay", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        assertEquals(2000L, retry.invoke(p, true, false))
        assertEquals(4000L, retry.invoke(p, true, false))
        assertEquals(5000L, retry.invoke(p, false, false))
        assertEquals(8000L, retry.invoke(p, true, false))
        repeat(10) { retry.invoke(p, true, false) }
        assertEquals(60000L, retry.invoke(p, true, false))
        assertEquals(35000L, retry.invoke(p, true, true))
        assertEquals(2000L, retry.invoke(p, true, false))
    }
    @Test fun `a running core alone must not count as verified`() {
        val type = runCatching { Class.forName("com.v2ray.ang.simple.ReliabilityPolicy") }.getOrNull()
        assertNotNull(type, "Missing tunnel verification policy")
        val policy = type!!.getDeclaredConstructor().newInstance()
        assertEquals(false, type.getMethod("getVerified").invoke(policy))
    }
}
