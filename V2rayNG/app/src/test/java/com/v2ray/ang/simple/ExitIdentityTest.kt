package com.v2ray.ang.simple

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExitIdentityTest {
    @Test fun `rejects coercible non boolean success and non string ip`() {
        assertNull(ExitIdentity.parse("""{"success":"true","ip":"8.8.8.8","country_code":"US"}"""))
        assertNull(ExitIdentity.parse("""{"success":true,"ip":1234,"country_code":"US"}"""))
    }
    @Test fun `accepts only validated numeric IP and ISO country`() {
        assertEquals(ExitIdentity("8.8.8.8", "US"), ExitIdentity.parse("""{"success":true,"ip":"8.8.8.8","country_code":"US"}"""))
        assertEquals("2001:4860:4860::8888", ExitIdentity.parse("""{"success":true,"ip":"2001:4860:4860::8888","country_code":"DE"}""")?.ip)
        for (ip in listOf("example.com", "1.2.3.999", "1.2.3", "::gg", "<b>8.8.8.8</b>", "fe80::1%wlan0")) {
            assertNull(ExitIdentity.parse("""{"success":true,"ip":"$ip","country_code":"US"}"""), ip)
        }
        for (country in listOf("ZZ", "USA", "us", "<b>")) {
            assertNull(ExitIdentity.parse("""{"success":true,"ip":"8.8.8.8","country_code":"$country"}"""), country)
        }
        assertNull(ExitIdentity.parse("<html>unavailable</html>"))
        assertNull(ExitIdentity.parse("""{"success":false,"ip":"8.8.8.8","country_code":"US"}"""))
    }
}
