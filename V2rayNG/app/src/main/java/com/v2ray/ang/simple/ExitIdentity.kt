package com.v2ray.ang.simple

import com.google.gson.JsonParser
import java.net.InetAddress
import java.util.Locale

data class ExitIdentity(val ip: String, val countryCode: String) {
    val flag: String get() = countryCode.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    companion object {
        fun parse(body: String): ExitIdentity? = runCatching {
            require(body.length <= 4096)
            val obj = JsonParser.parseString(body).asJsonObject
            require(obj.getAsJsonPrimitive("success").isBoolean && obj.get("success").asBoolean)
            require(obj.getAsJsonPrimitive("ip").isString)
            require(obj.getAsJsonPrimitive("country_code").isString)
            val ip = obj.get("ip").asString
            val country = obj.get("country_code").asString
            require(country in Locale.getISOCountries().toSet())
            val ipv4 = ip.split('.').let { parts -> parts.size == 4 && parts.all {
                it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() <= 255
            } }
            // Character gate guarantees getByName never receives a hostname or scoped address.
            val ipv6 = ':' in ip && ip.matches(Regex("[0-9a-fA-F:.]{2,45}")) &&
                runCatching { InetAddress.getByName(ip); true }.getOrDefault(false)
            require(ipv4 || ipv6)
            ExitIdentity(ip, country)
        }.getOrNull()
    }
}
