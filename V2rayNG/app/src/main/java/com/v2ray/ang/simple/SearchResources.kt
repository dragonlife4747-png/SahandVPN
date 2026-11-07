package com.v2ray.ang.simple

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Owns every blocking resource of a single explicit search request. */
class SearchResources : AutoCloseable {
    private val stopped = AtomicBoolean(false)
    private val closers = ConcurrentLinkedDeque<() -> Unit>()
    private val pools = ConcurrentLinkedDeque<ExecutorService>()
    val closed: Boolean get() = stopped.get()

    fun <T> own(value: T, close: (T) -> Unit): T {
        val once = AtomicBoolean(false)
        val closer = { if (once.compareAndSet(false, true)) { runCatching { close(value) }; Unit } }
        closers.addFirst(closer)
        if (closed && closers.remove(closer)) closer()
        return value
    }

    fun pool(pool: ExecutorService): ExecutorService {
        pools.add(pool)
        own(pool) { it.shutdownNow() }
        return pool
    }

    /** Non-waiting interruption, safe on the Android service main thread. */
    fun cancel() {
        stopped.set(true)
        while (true) (closers.pollFirst() ?: break).invoke()
    }

    override fun close() {
        cancel()
        // Blocking I/O has already been closed, not merely interrupted.
        var interrupted = Thread.interrupted()
        pools.forEach { pool ->
            while (!pool.isTerminated) {
                try { pool.awaitTermination(100, TimeUnit.MILLISECONDS) }
                catch (_: InterruptedException) { interrupted = true }
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
}
