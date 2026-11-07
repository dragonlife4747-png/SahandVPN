package com.v2ray.ang.update

object UpdatePolicy {
    const val REPO = "dragonlife4747-png/SahandVPN"
    const val MAX_BYTES = 150L * 1024 * 1024
    fun asset(names: List<String>, tag: String, abis: List<String>): String? {
        if (version(tag) == null) return null
        return (abis.filter { it in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") } + "universal")
            .map { "SahandVPN_${tag.removePrefix("v")}_${it}.apk" }.firstOrNull { it in names }
    }
    fun downloadUrl(url: String, initial: Boolean): Boolean = runCatching {
        val u = java.net.URI(url)
        u.scheme == "https" && u.rawUserInfo == null && u.fragment == null && u.port == -1 &&
            if (initial) u.host == "github.com" && u.rawPath.startsWith("/$REPO/releases/download/") && u.rawQuery == null
            else u.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }.getOrDefault(false)
    fun archive(pkg: String, name: String, code: Long, expected: String, installedCode: Long, signers: Set<String>, installedSigners: Set<String>): Boolean =
        pkg == "com.sahand.vpn" && name == expected && version(expected) != null && code > installedCode && signers.isNotEmpty() && signers == installedSigners
    fun version(value: String): List<Int>? {
        if (!Regex("v?(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})(\\.(0|[1-9][0-9]{0,8}))?").matches(value)) return null
        return value.removePrefix("v").split('.').map { it.toInt() }.let { if (it.size == 2) it + 0 else it }
    }
    fun newer(tag: String, installed: String): Boolean {
        val a = version(tag) ?: return false
        val b = version(installed) ?: return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
}
