package com.v2ray.ang.update

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UpdatePolicyTest {
    @Test fun assetsAreExactAndAbiOrdered() {
        val names = listOf("SahandVPN_1.8_universal.apk", "SahandVPN_1.8_arm64-v8a.apk", "evil_arm64-v8a.apk")
        assertEquals(names[1], UpdatePolicy.asset(names, "v1.8", listOf("arm64-v8a")))
        assertEquals(names[0], UpdatePolicy.asset(names, "v1.8", listOf("x86")))
        assertNull(UpdatePolicy.asset(listOf("../SahandVPN_1.8_universal.apk"), "v1.8", emptyList()))
    }
    @Test fun urlsRejectUntrustedOrigins() {
        assertTrue(UpdatePolicy.downloadUrl("https://github.com/dragonlife4747-png/SahandVPN/releases/download/v1.8/SahandVPN_1.8_arm64-v8a.apk", true))
        for (url in listOf("http://github.com/a", "https://github.com.evil/a", "https://user@github.com/a", "https://evil.com/a", "https://github.com:444/a")) assertFalse(UpdatePolicy.downloadUrl(url, false), url)
        assertTrue(UpdatePolicy.downloadUrl("https://release-assets.githubusercontent.com/github-production-release-asset/123?a=b", false))
        assertFalse(UpdatePolicy.downloadUrl("https://github.com/evil/repo/releases/download/v1.8/a.apk", true))
    }
    @Test fun archiveMustMatchReleaseInstalledIdentityAndBeNewer() {
        assertTrue(UpdatePolicy.archive("com.sahand.vpn", "1.8", 4010009, "1.8", 4010008, setOf("key"), setOf("key")))
        assertFalse(UpdatePolicy.archive("evil", "1.8", 4010009, "1.8", 4010008, setOf("key"), setOf("key")))
        assertFalse(UpdatePolicy.archive("com.sahand.vpn", "1.9", 4010009, "1.8", 4010008, setOf("key"), setOf("key")))
        assertFalse(UpdatePolicy.archive("com.sahand.vpn", "1.8", 10009, "1.8", 4010008, setOf("key"), setOf("key")))
        assertFalse(UpdatePolicy.archive("com.sahand.vpn", "1.8", 4010009, "1.8", 4010008, setOf("evil"), setOf("key")))
        assertFalse(UpdatePolicy.archive("com.sahand.vpn", "1.8", 4010009, "1.8", 4010008, emptySet(), emptySet()))
    }
    @Test fun numericVersionsNotLexical() {
        assertTrue(UpdatePolicy.newer("v1.10", "1.9"))
        assertFalse(UpdatePolicy.newer("v1.8", "1.8.0"))
        assertFalse(UpdatePolicy.newer("v1.8-rc1", "1.7"))
        assertFalse(UpdatePolicy.newer("v99999999999999999999.1", "1.7"))
    }
}
