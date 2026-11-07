package com.v2ray.ang.simple

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Probes several profiles with a **single** Xray instance.
 *
 * Every profile becomes one outbound plus a dedicated loopback SOCKS inbound, so the batch is
 * measured in parallel through local ports instead of starting one core per profile. That keeps
 * memory flat and avoids the native deadlock that appeared with several concurrent core instances.
 */
class SimpleProbeEngine(private val context: Context) {

    data class ProbeResult(
        val guid: String,
        val delayMs: Long,
        val httpsOk: Boolean,
        val bytesPerSecond: Long,
    )

    private data class Slot(val guid: String, val index: Int, val port: Int)

    private var startedController: CoreController? = null

    /** Rejects outbounds the core is known to refuse, before a whole batch pays for it. */
    private fun isSupportedOutbound(outbound: JsonObject): Boolean {
        if (!OutboundSafety.accepts(outbound)) return false
        val protocol = outbound.get("protocol")?.asString ?: return false
        val settings = outbound.getAsJsonObject("settings")
        val stream = outbound.getAsJsonObject("streamSettings")
        val security = stream?.get("security")?.asString?.lowercase().orEmpty()

        // Xray 26 removed allowInsecure; such profiles cannot start at all, so skip them.
        if (stream?.getAsJsonObject("tlsSettings")?.get("allowInsecure")?.asBoolean == true) {
            return false
        }

        return when (protocol) {
            "shadowsocks" -> {
                val method = settings?.get("method")?.asString.orEmpty().lowercase()
                val password = settings?.get("password")?.asString
                method in SUPPORTED_SS_METHODS && !password.isNullOrBlank()
            }

            "vless" -> {
                val address = settings?.getAsJsonArray("vnext")
                    ?.firstOrNull()?.asJsonObject?.get("address")?.asString.orEmpty()
                val hasSecurity = security == "tls" || security == "reality"
                if (!hasSecurity) {
                    // Plain VLESS is only accepted for domain names, never bare IPs.
                    address.isNotBlank() && !isIpLiteral(address)
                } else if (security == "reality") {
                    val publicKey = stream?.getAsJsonObject("realitySettings")
                        ?.get("publicKey")?.asString.orEmpty()
                    publicKey.length >= 40
                } else {
                    true
                }
            }

            "vmess", "trojan" -> true
            else -> false
        }
    }

    private fun isIpLiteral(host: String): Boolean {
        return host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || host.contains(':')
    }

    /**
     * @param deep also verify TLS and measure throughput (slower, use for the finalists only)
     */
    fun probe(guids: List<String>, deep: Boolean, timeoutMs: Long = 6_000): List<ProbeResult> {
        if (guids.isEmpty()) return emptyList()

        val slots = mutableListOf<Slot>()
        val outbounds = JsonArray()
        val inbounds = JsonArray()
        val rules = JsonArray()

        guids.forEachIndexed { index, guid ->
            val profileConfig = runCatching {
                CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
            }.getOrNull()
            if (profileConfig == null || !profileConfig.status) return@forEachIndexed

            val proxyOutbound = runCatching {
                JsonParser.parseString(profileConfig.content).asJsonObject
                    .getAsJsonArray("outbounds")
                    ?.firstOrNull {
                        it.asJsonObject.get("tag")?.asString == AppConfig.TAG_PROXY
                    }
            }.getOrNull() ?: return@forEachIndexed

            if (!isSupportedOutbound(proxyOutbound.asJsonObject)) return@forEachIndexed

            val slotIndex = slots.size
            val port = runCatching { Utils.findRandomFreePort() }.getOrDefault(0)
            if (port <= 0) return@forEachIndexed
            val slot = Slot(guid, slotIndex, port)
            slots.add(slot)

            val outbound = proxyOutbound.asJsonObject.deepCopy()
            outbound.addProperty("tag", "o$slotIndex")
            outbounds.add(outbound)

            inbounds.add(
                JsonObject().apply {
                    addProperty("tag", "in$slotIndex")
                    addProperty("listen", "127.0.0.1")
                    addProperty("port", port)
                    addProperty("protocol", "socks")
                    add(
                        "settings",
                        JsonObject().apply {
                            addProperty("auth", "noauth")
                            addProperty("udp", false)
                        }
                    )
                }
            )

            rules.add(
                JsonObject().apply {
                    addProperty("type", "field")
                    add("inboundTag", JsonArray().apply { add("in$slotIndex") })
                    addProperty("outboundTag", "o$slotIndex")
                }
            )
        }

        if (slots.isEmpty()) return emptyList()

        outbounds.add(
            JsonObject().apply {
                addProperty("tag", AppConfig.TAG_DIRECT)
                addProperty("protocol", "freedom")
            }
        )
        outbounds.add(
            JsonObject().apply {
                addProperty("tag", AppConfig.TAG_BLOCKED)
                addProperty("protocol", "blackhole")
            }
        )

        val config = JsonObject().apply {
            add("log", JsonObject().apply { addProperty("loglevel", "error") })
            add("inbounds", inbounds)
            add("outbounds", outbounds)
            add(
                "routing",
                JsonObject().apply {
                    addProperty("domainStrategy", "AsIs")
                    add("rules", rules)
                }
            )
        }.toString()

        val started = try {
            val controller = CoreNativeManager.newCoreController(NoopCallback)
            controller.startLoop(config, 0)
            startedController = controller
            true
        } catch (e: Throwable) {
            LogUtil.w(AppConfig.TAG, "SimpleProbeEngine: batch failed (${slots.size} slots): ${e.message}")
            false
        }

        if (!started) {
            // A single unusable profile rejects the whole config: halve the batch and retry.
            if (guids.size > 1) {
                val half = guids.size / 2
                return probe(guids.subList(0, half), deep, timeoutMs) +
                    probe(guids.subList(half, guids.size), deep, timeoutMs)
            }
            return emptyList()
        }

        return try {
            Thread.sleep(220)
            probeAll(slots, deep, timeoutMs)
        } catch (e: Throwable) {
            LogUtil.w(AppConfig.TAG, "SimpleProbeEngine: probing failed: ${e.message}")
            emptyList()
        } finally {
            runCatching { startedController?.stopLoop() }
            startedController = null
        }
    }

    private fun probeAll(slots: List<Slot>, deep: Boolean, timeoutMs: Long): List<ProbeResult> {
        val results = CopyOnWriteArrayList<ProbeResult>()
        val pool = Executors.newFixedThreadPool(minOf(8, slots.size))
        slots.forEach { slot ->
            pool.execute {
                val delay = httpDelay(slot.port, timeoutMs, secure = false)
                if (delay <= 0) return@execute
                var httpsOk = false
                var bytesPerSecond = 0L
                if (deep) {
                    httpsOk = httpDelay(slot.port, timeoutMs, secure = true) > 0
                    if (httpsOk) bytesPerSecond = throughput(slot.port, timeoutMs)
                }
                results.add(ProbeResult(slot.guid, delay, httpsOk, bytesPerSecond))
            }
        }
        pool.shutdown()
        runCatching { pool.awaitTermination(timeoutMs * 2, TimeUnit.MILLISECONDS) }
        return results.toList()
    }

    private fun client(port: Int, timeoutMs: Long): OkHttpClient =
        OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs + 2_000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()

    /** Round trip of a 204 request through the loopback SOCKS inbound, or -1. */
    private fun httpDelay(port: Int, timeoutMs: Long, secure: Boolean): Long {
        val url = if (secure) HTTPS_PROBE_URL else HTTP_PROBE_URL
        return try {
            val request = Request.Builder().url(url).header("Connection", "close").get().build()
            val started = System.currentTimeMillis()
            client(port, timeoutMs).newCall(request).execute().use { response ->
                if (response.code in 200..204) System.currentTimeMillis() - started else -1L
            }
        } catch (_: Throwable) {
            -1L
        }
    }

    /**
     * Downloads a small payload through the proxy and returns bytes per second.
     * Returns 0 when the transfer fails; the caller then ranks by latency only.
     */
    private fun throughput(port: Int, timeoutMs: Long): Long {
        return try {
            val request = Request.Builder().url(SPEED_URL).header("Connection", "close").get().build()
            val client = client(port, timeoutMs)
            val started = System.nanoTime()
            var total = 0L
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return 0L
                val body = response.body ?: return 0L
                val buffer = ByteArray(16 * 1024)
                body.byteStream().use { stream ->
                    while (total < MAX_SPEED_BYTES) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        total += read
                    }
                }
            }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            if (elapsedMs <= 0 || total <= 0) 0L else total * 1000 / elapsedMs
        } catch (_: Throwable) {
            0L
        }
    }

    private object NoopCallback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }

    companion object {
        const val BATCH_SIZE = 12
        private val SUPPORTED_SS_METHODS = setOf(
            "aes-128-gcm", "aes-256-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305",
            "xchacha20-poly1305", "xchacha20-ietf-poly1305",
            "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
            "none", "plain"
        )
        private const val HTTP_PROBE_URL = "http://www.gstatic.com/generate_204"
        private const val HTTPS_PROBE_URL = "https://www.gstatic.com/generate_204"
        private const val SPEED_URL = "https://speed.cloudflare.com/__down?bytes=196608"
        private const val MAX_SPEED_BYTES = 262_144L
    }
}
