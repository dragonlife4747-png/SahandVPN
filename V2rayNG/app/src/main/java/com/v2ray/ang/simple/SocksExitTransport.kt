package com.v2ray.ang.simple

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** No HttpUtil: this application's UID is intentionally excluded from the VPN. */
class SocksExitTransport(private val deliver: (() -> Unit) -> Unit) : ExitTransport {
    override fun start(port: Int, done: (ExitIdentity?) -> Unit): () -> Unit {
        val http = runCatching { client(port) }.getOrElse {
            deliver { done(null) }; return {}
        }
        val call = http.newCall(Request.Builder().url(ENDPOINT).header("Connection", "close").build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { deliver { done(null) } }
            override fun onResponse(call: Call, response: Response) {
                val identity = runCatching {
                    response.use {
                        if (!it.isSuccessful) return@use null
                        val body = it.body
                        // Read a bounded body even if a malicious response has no content length.
                        val source = body.source()
                        source.request(4097)
                        if (source.buffer.size > 4096) null else ExitIdentity.parse(source.readUtf8())
                    }
                }.getOrNull()
                deliver { done(identity) }
            }
        })
        return { call.cancel(); http.connectionPool.evictAll() }
    }
    companion object {
        const val ENDPOINT = "https://ipwho.is/?fields=success,ip,country_code"
        fun client(port: Int): OkHttpClient {
            require(port in 1..65535)
            return OkHttpClient.Builder()
                // OkHttp creates an unresolved target for SOCKS; DNS is performed by the tunnel.
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build() // standard trust manager + hostname verifier, no cookies or API secrets
        }
    }
}
