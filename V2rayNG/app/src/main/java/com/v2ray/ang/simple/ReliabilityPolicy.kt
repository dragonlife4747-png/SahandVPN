package com.v2ray.ang.simple

/** Pure state policy shared by the single daemon health loop. */
class ReliabilityPolicy {
    @Volatile var verified = false
        private set
    @Volatile var requested = false
        private set
    @Volatile var generation = 0L
        private set

    private var retries = 0
    @Synchronized fun retryDelay(online: Boolean, healthy: Boolean): Long {
        if (!online) return 5_000L
        if (healthy) { retries = 0; return 35_000L }
        return minOf(60_000L, 2_000L shl minOf(retries++, 5))
    }
    @Synchronized fun start(): Long {
        retries = 0
        requested = true
        verified = false
        return ++generation
    }
    @Synchronized fun stop() {
        requested = false
        verified = false
        generation++
    }
    @Synchronized fun accept(token: Long, healthy: Boolean): Boolean {
        if (!requested || token != generation) return false
        verified = healthy
        return true
    }
}
