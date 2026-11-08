package com.v2ray.ang.update
import java.io.*
object UpdateTransfer {
    fun copy(input: InputStream, output: OutputStream, size: Long, digest: String?, cancelled: () -> Boolean, progress: (Int) -> Unit) {
        if (size !in 1..UpdatePolicy.MAX_BYTES) throw IOException("Invalid asset size")
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            if (cancelled()) throw java.util.concurrent.CancellationException()
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > size || total > UpdatePolicy.MAX_BYTES) throw IOException("Asset too large")
            hash.update(buffer, 0, n)
            output.write(buffer, 0, n)
            progress((100 * total / size).toInt())
        }
        if (total != size) throw IOException("Incomplete asset")
        val actual = "sha256:" + hash.digest().joinToString("") { "%02x".format(it) }
        if (digest != null && !actual.equals(digest, true)) throw IOException("Digest mismatch")
    }
}
