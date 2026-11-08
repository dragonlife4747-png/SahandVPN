package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExitLookupTest {
    @Test fun `synchronous completion publishes identity without reading owner initializer`() {
        val states = mutableListOf<Pair<ExitIdentity?, Boolean>>()
        val expected = ExitIdentity("8.8.8.8", "US")
        val transport = object : ExitTransport {
            override fun start(port: Int, done: (ExitIdentity?) -> Unit): () -> Unit {
                done(expected)
                return {}
            }
        }
        val lookup = ExitLookup(transport) { identity, loading -> states.add(identity to loading) }
        lookup.verified(10808)
        assertEquals(listOf(null to true, expected to false), states)
        lookup.clear()
        assertEquals(null to false, states.last())
    }
    private class Fake : ExitTransport {
        val callbacks = mutableListOf<(ExitIdentity?) -> Unit>()
        var cancelled = 0
        override fun start(port: Int, done: (ExitIdentity?) -> Unit): () -> Unit {
            callbacks.add(done)
            return { cancelled++ }
        }
    }
    @Test fun `disconnect and recovery clear immediately and reject stale callbacks`() {
        val net = Fake()
        val lookup = ExitLookup(net) { _, _ -> }
        lookup.verified(10808)
        val old = net.callbacks.single()
        lookup.clear()
        lookup.verified(10808)
        old(ExitIdentity("8.8.8.8", "US"))
        assertNull(lookup.identity)
        net.callbacks.last()(ExitIdentity("1.1.1.1", "AU"))
        assertEquals("AU", lookup.identity?.countryCode)
        lookup.clear()
        assertNull(lookup.identity)
        assertFalse(lookup.isLoading)
        assertEquals(2, net.cancelled)
    }
    @Test fun `health successes do not poll and transient failure stays unavailable`() {
        val net = Fake()
        val lookup = ExitLookup(net) { _, _ -> }
        lookup.verified(10808)
        net.callbacks.single()(null)
        repeat(10) { lookup.verified(10808) }
        assertEquals(1, net.callbacks.size)
        assertNull(lookup.identity)
        assertFalse(lookup.isLoading)
        lookup.clear() // unverified failover invalidates the prior exit
        lookup.verified(10808)
        assertTrue(lookup.isLoading)
        assertEquals(2, net.callbacks.size)
    }
}