package com.v2ray.ang.simple

import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

/** Private, bounded TCP attempt history. Only endpoint hashes, never addresses or profiles. */
class EndpointHistory(private val file: File) {
    data class Entry(val key: String, val failures: Int, val at: Long)
    private val entries = runCatching {
        Gson().fromJson(file.readText(), Array<Entry>::class.java).toMutableList()
    }.getOrElse { mutableListOf() }
    private fun key(endpoint: String) = MessageDigest.getInstance("SHA-256")
        .digest(endpoint.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
    @Synchronized fun eligible(endpoint: String, now: Long): Boolean {
        val entry = entries.firstOrNull { it.key == key(endpoint) } ?: return true
        return entry.failures == 0 || now < entry.at || now - entry.at >= minOf(900_000L, 60_000L * entry.failures)
    }
    @Synchronized fun record(endpoint: String, success: Boolean, now: Long) {
        val hash = key(endpoint)
        val old = entries.firstOrNull { it.key == hash }
        entries.removeAll { it.key == hash }
        entries.add(Entry(hash, if (success) 0 else minOf(15, (old?.failures ?: 0) + 1), now))
        while (entries.size > 512) entries.removeAt(0)
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(Gson().toJson(entries))
        check(temp.renameTo(file))
    }
}
