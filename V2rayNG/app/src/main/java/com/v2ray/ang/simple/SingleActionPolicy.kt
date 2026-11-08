package com.v2ray.ang.simple

enum class Action { NONE, SEARCH, CONNECT }

/** A user request owns at most one search and its completion. Never persisted as auto-connect consent. */
class SingleActionPolicy {
    var generation = 0L
        private set
    var requested = false
        private set
    private var searching = false
    fun begin(online: Boolean, cached: Boolean): Action {
        if (requested || !online) return Action.NONE
        generation++
        requested = true
        searching = !cached
        return if (cached) Action.CONNECT else Action.SEARCH
    }
    fun searchFinished(token: Long, found: Boolean): Action {
        if (!requested || token != generation || !searching) return Action.NONE
        searching = false
        if (!found) { requested = false; return Action.NONE }
        return Action.CONNECT
    }
    /**
     * Two-button screen: the search and the connect action are requested explicitly instead of
     * being derived from the cache state. Returns false when a request is already in flight.
     */
    fun request(online: Boolean, search: Boolean): Boolean {
        if (requested || !online) return false
        generation++
        requested = true
        searching = search
        return true
    }

    fun cancel() { requested = false; searching = false }
    fun accepts(token: Long): Boolean = requested && token == generation
    fun exhausted(cached: Boolean): Boolean = searchFinished(generation, cached) == Action.CONNECT
}