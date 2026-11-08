package com.v2ray.ang.simple

interface ExitTransport {
    fun start(port: Int, done: (ExitIdentity?) -> Unit): () -> Unit
}

/** Main-thread owner; one request per verified tunnel generation, never a direct fallback. */
class ExitLookup(private val transport: ExitTransport, private val changed: (ExitIdentity?, Boolean) -> Unit) {
    var identity: ExitIdentity? = null; private set
    var loading = false; private set
    private var generation = 0L
    private var active = false
    private var cancel: (() -> Unit)? = null

    fun clear() {
        ++generation
        active = false
        val previous = cancel
        cancel = null
        loading = false
        identity = null
        previous?.invoke()
        changed(identity, loading)
    }

    fun verified(port: Int) {
        if (active) return
        active = true
        loading = true
        identity = null
        val token = ++generation
        changed(identity, loading)
        if (token != generation || !active) return
        val cancellation = transport.start(port) { result ->
            if (token == generation && active) {
                identity = result
                loading = false
                changed(identity, loading)
            }
        }
        // A synchronous transport/callback may clear or replace this generation.
        if (token == generation && active) cancel = cancellation else cancellation()
    }

    val isLoading: Boolean get() = loading
}
