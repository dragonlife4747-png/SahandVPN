package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SocksExitTransportTest {
    @Test fun `failed SOCKS request sends unresolved endpoint hostname and never falls back`() {
        ServerSocket(0).use { server ->
            var destination = ""
            val seen = CountDownLatch(1)
            val worker = Thread {
                server.accept().use { socket ->
                    socket.soTimeout = 4000
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    assertEquals(5, input.read())
                    val methods = input.read(); repeat(methods) { input.read() }
                    output.write(byteArrayOf(5, 0)); output.flush()
                    assertEquals(5, input.read()); assertEquals(1, input.read()); input.read()
                    assertEquals(3, input.read()) // SOCKS domain, NOT locally resolved address
                    val length = input.read()
                    destination = String(ByteArray(length) { input.read().toByte() })
                    input.read(); input.read()
                    seen.countDown()
                    output.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)); output.flush()
                }
            }
            worker.start()
            val done = CountDownLatch(1)
            var result: ExitIdentity? = ExitIdentity("8.8.8.8", "US")
            val transport = SocksExitTransport { it() }
            transport.start(server.localPort) { result = it; done.countDown() }
            assertTrue(seen.await(5, TimeUnit.SECONDS))
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals("ipwho.is", destination)
            assertNull(result)
            worker.join(1000)
        }
    }
    @Test fun `explicit proxy and bounded verified HTTPS configuration`() {
        val client = SocksExitTransport.client(10808)
        assertEquals(java.net.Proxy.Type.SOCKS, client.proxy?.type())
        assertFalse(client.retryOnConnectionFailure)
        assertFalse(client.followRedirects)
        assertEquals(10000, client.callTimeoutMillis)
        assertThrows(IllegalArgumentException::class.java) { SocksExitTransport.client(0) }
    }
}
