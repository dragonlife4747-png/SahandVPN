package com.v2ray.ang.update

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** No credential or shared VPN HTTP client: TLS and redirects are confined here. */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    data class Release(val tag: String, val url: String, val size: Long, val digest: String?)
    var release by mutableStateOf<Release?>(null); private set
    var visible by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(0); private set
    var failed by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var awaitingPermission = false
    var autoInstallPending = false
    private var job: Job? = null
    private val context = app
    private val prefs = app.getSharedPreferences("release_updates", 0)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES)
        .followRedirects(false).followSslRedirects(false).build()
    val apk: File get() = File(context.cacheDir, "updates/update.apk")
    private val partial: File get() = File(context.cacheDir, "updates/update.part")

    init { check() }

    fun checkManually() {
        prefs.edit().remove("checked").remove("dismissed").apply()
        check(manual = true)
    }

    private fun check(manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) android.widget.Toast.makeText(context, com.v2ray.ang.R.string.update_checking, android.widget.Toast.LENGTH_SHORT).show()
            val now = System.currentTimeMillis()
            val last = prefs.getLong("checked", 0)
            // Cache only successful responses, back off failures for 15 min; never block VPN use.
            val cached = prefs.getString("metadata", null)
            val interval = if (cached != null) 6 * 60 * 60_000L else 15 * 60_000L
            val fresh = now >= last && now - last < interval
            try {
                val json = if (fresh) cached else withContext(Dispatchers.IO) {
                    prefs.edit().putLong("checked", now).apply()
                    client.newCall(Request.Builder().url("https://api.github.com/repos/${UpdatePolicy.REPO}/releases/latest")
                        .header("Accept", "application/vnd.github+json").header("User-Agent", "SahandVPN-Updater").build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Release check failed")
                        val body = response.body ?: throw IOException("Empty response")
                        if (body.contentLength() > 1024 * 1024) throw IOException("Metadata too large")
                        val bytes = body.byteStream().use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (output.size() <= 1024 * 1024) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                            }
                            output.toByteArray()
                        }
                        if (bytes.size > 1024 * 1024) throw IOException("Metadata too large")
                        String(bytes, Charsets.UTF_8)
                    }.also { prefs.edit().putString("metadata", it).apply() }
                }
                if (json != null) release = parse(json)
                visible = release != null && prefs.getString("dismissed", null) != release?.tag
                if (manual && release == null) android.widget.Toast.makeText(context, com.v2ray.ang.R.string.update_no_newer, android.widget.Toast.LENGTH_LONG).show()
                if (release == null) withContext(Dispatchers.IO) { apk.delete(); partial.delete() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (manual) android.widget.Toast.makeText(context, com.v2ray.ang.R.string.update_unavailable, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun parse(json: String): Release? {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("draft").asBoolean || root.get("prerelease").asBoolean) return null
        val tag = root.get("tag_name").asString
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        if (!UpdatePolicy.newer(tag, installed.versionName ?: "")) return null
        val assets = root.getAsJsonArray("assets").map { it.asJsonObject }
        val selected = UpdatePolicy.asset(assets.map { it.get("name").asString }, tag, Build.SUPPORTED_ABIS.toList()) ?: return null
        val a = assets.first { it.get("name").asString == selected }
        if (a.get("state").asString != "uploaded") return null
        val url = a.get("browser_download_url").asString
        if (url != "https://github.com/${UpdatePolicy.REPO}/releases/download/$tag/$selected" || !UpdatePolicy.downloadUrl(url, true)) return null
        val size = a.get("size").asLong
        if (size !in 1..UpdatePolicy.MAX_BYTES) return null
        val digest = a.get("digest")?.takeUnless { it.isJsonNull }?.asString
        if (digest != null && !Regex("sha256:[0-9a-fA-F]{64}").matches(digest)) return null
        return Release(tag, url, size, digest)
    }

    fun dismiss() {
        if (busy) job?.cancel()
        visible = false
        release?.let { prefs.edit().putString("dismissed", it.tag).apply() }
    }

    fun download() {
        val r = release ?: return
        if (busy) return
        busy = true; failed = false; progress = 0; ready = false
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apk.parentFile!!.mkdirs(); apk.delete(); partial.delete()
                    try {
                        var url = r.url
                        var completed = false
                        for (hop in 0..5) {
                            ensureActive()
                            if (!UpdatePolicy.downloadUrl(url, hop == 0)) throw IOException("Untrusted download")
                            val call = client.newCall(Request.Builder().url(url).build())
                            val cancelHandle = coroutineContext.job.invokeOnCompletion { if (it != null) call.cancel() }
                            try {
                                call.execute().use { response ->
                                    if (response.code in setOf(301, 302, 303, 307, 308)) {
                                        url = response.header("Location")?.let { response.request.url.resolve(it)?.toString() } ?: throw IOException("Missing redirect")
                                    } else {
                                        if (!response.isSuccessful) throw IOException("Download failed")
                                        val body = response.body ?: throw IOException("Empty APK")
                                        if (body.contentLength() >= 0 && body.contentLength() != r.size) throw IOException("Length mismatch")
                                        val task = coroutineContext.job
                                        body.byteStream().use { input -> partial.outputStream().use { output ->
                                            UpdateTransfer.copy(input, output, r.size, r.digest, { !task.isActive }) { n -> progress = n }
                                        } }
                                        verify(partial, r)
                                        if (!partial.renameTo(apk)) throw IOException("Cannot save APK")
                                        completed = true
                                    }
                                }
                            } finally { cancelHandle.dispose() }
                            if (completed) break
                        }
                        if (!completed) throw IOException("Too many redirects")
                    } finally { partial.delete() }
                }
                autoInstallPending = true
                ready = true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { apk.delete(); failed = true }
            finally { busy = false }
        }
    }

    /** Re-check immediately before each installer handoff, including return from Settings. */
    fun verifyReady(): Boolean = runCatching {
        verify(apk, release ?: error("No release")); true
    }.getOrElse { ready = false; failed = true; apk.delete(); false }

    fun installerFailed() { failed = true }

    @Suppress("DEPRECATION")
    private fun verify(file: File, r: Release) {
        if (!file.isFile || file.length() != r.size) throw IOException("Missing APK")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: throw IOException("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun code(p: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
        fun signers(p: PackageInfo): Set<String> {
            val certs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
            return certs.orEmpty().map { cert -> MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        }
        if (!UpdatePolicy.archive(archive.packageName, archive.versionName ?: "", code(archive), r.tag.removePrefix("v"), code(installed), signers(archive), signers(installed))) throw IOException("APK identity rejected")
        if (Build.VERSION.SDK_INT >= 24 && archive.applicationInfo?.minSdkVersion?.let { it > Build.VERSION.SDK_INT } == true) throw IOException("Android version unsupported")
        if (r.digest != null) {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }
            if (!("sha256:" + hash.digest().joinToString("") { "%02x".format(it) }).equals(r.digest, true)) throw IOException("Digest mismatch")
        }
    }
}
