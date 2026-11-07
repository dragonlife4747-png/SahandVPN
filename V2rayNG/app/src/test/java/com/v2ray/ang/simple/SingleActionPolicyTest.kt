package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SingleActionPolicyTest {
    @Test fun `one search then single connect`() {
        val p = SingleActionPolicy()
        assertEquals(Action.SEARCH, p.begin(true, false))
        val token = p.generation
        assertEquals(Action.CONNECT, p.searchFinished(token, true))
        assertEquals(Action.NONE, p.searchFinished(token, true))
    }
    @Test fun `cancel stops everything`() {
        val p = SingleActionPolicy()
        p.begin(true, false)
        val token = p.generation
        p.cancel()
        assertFalse(p.accepts(token))
        assertEquals(Action.NONE, p.searchFinished(token, true))
    }
    @Test fun `cached skips search and connects once`() {
        val p = SingleActionPolicy()
        assertEquals(Action.CONNECT, p.begin(true, true))
        // second request is a duplicate since generation already consumed
        assertEquals(Action.NONE, p.begin(true, true))
    }
    @Test fun `offline blocks and duplicate request does not fork`() {
        val p = SingleActionPolicy()
        assertEquals(Action.NONE, p.begin(false, false))
        assertFalse(p.requested)
        p.begin(true, false)
        val token = p.generation
        assertEquals(Action.NONE, p.begin(true, false))
        assertEquals(token, p.generation)
    }
    @Test fun `previous generation rejected after disconnect`() {
        val p = SingleActionPolicy()
        p.begin(true, false)
        val old = p.generation
        p.cancel()
        p.begin(true, false)
        val new = p.generation
        assertNotEquals(old, new)
    }
    @Test fun `only current request accepted`() {
        val p = SingleActionPolicy()
        p.begin(true, true)
        val token = p.generation
        p.cancel()
        assertFalse(p.accepts(token))
    }
}