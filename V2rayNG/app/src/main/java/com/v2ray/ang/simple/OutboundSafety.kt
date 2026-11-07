package com.v2ray.ang.simple

import com.google.gson.JsonObject

/** Automatic selection never downgrades transport authentication. Malformed input fails closed. */
object OutboundSafety {
    fun accepts(outbound: JsonObject): Boolean = runCatching {
        val stream = outbound.getAsJsonObject("streamSettings")
        val security = stream?.get("security")?.asString.orEmpty().lowercase()
        if (stream?.getAsJsonObject("tlsSettings")?.get("allowInsecure")?.asBoolean == true) return false
        when (outbound.get("protocol")?.asString) {
            "vless", "trojan" -> security in setOf("tls", "reality")
            else -> true
        }
    }.getOrDefault(false)
}
