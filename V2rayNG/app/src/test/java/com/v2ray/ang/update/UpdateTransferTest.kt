package com.v2ray.ang.update

import java.io.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UpdateTransferTest {
    @Test fun boundedCopyChecksLengthDigestAndCancellation() {
        val data = "apk bytes".toByteArray()
        val out = ByteArrayOutputStream()
        assertThrows(IOException::class.java) { UpdateTransfer.copy(data.inputStream(), out, 10, null, { false }) {} }
        assertThrows(IOException::class.java) { UpdateTransfer.copy(data.inputStream(), out, 2, null, { false }) {} }
        assertThrows(IOException::class.java) { UpdateTransfer.copy(data.inputStream(), out, data.size.toLong(), "sha256:" + "0".repeat(64), { false }) {} }
        assertThrows(java.util.concurrent.CancellationException::class.java) { UpdateTransfer.copy(data.inputStream(), out, data.size.toLong(), null, { true }) {} }
        val good = ByteArrayOutputStream()
        UpdateTransfer.copy(data.inputStream(), good, data.size.toLong(), null, { false }) {}
        assertArrayEquals(data, good.toByteArray())
    }
}
