package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SearchResourcesTest {
    @Test fun `close interrupts workers and closes sockets before returning`() {
        val resources = SearchResources()
        val server = ServerSocket(0)
        val socket = resources.own(Socket("127.0.0.1", server.localPort)) { it.close() }
        val peer = server.accept()
        val pool = resources.pool(Executors.newFixedThreadPool(2))
        val entered = CountDownLatch(1)
        pool.execute { entered.countDown(); runCatching { Thread.sleep(60_000) } }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        resources.close()
        assertTrue(socket.isClosed)
        assertTrue(pool.isTerminated)
        assertTrue(resources.closed)
        peer.close(); server.close()
    }
    @Test fun `late resources cannot survive cancellation and cleanup is idempotent`() {
        val resources = SearchResources()
        resources.close(); resources.close()
        val socket = resources.own(Socket()) { it.close() }
        assertTrue(socket.isClosed)
        assertTrue(resources.pool(Executors.newSingleThreadExecutor()).isTerminated)
    }
    @Test fun `cleanup survives one failing closer`() {
        val resources = SearchResources()
        val events = mutableListOf<String>()
        resources.own("one") { events.add(it) }
        resources.own("bad") { error("failure") }
        resources.own("two") { events.add(it) }
        resources.close()
        assertEquals(listOf("two", "one"), events)
    }
}
