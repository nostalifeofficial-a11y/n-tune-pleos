package kr.nostalife.companion
import org.junit.Assert.*
import org.junit.Test

class ConnectionTest {
    @Test fun publicPairingIsScopedToApprovedHttpsOriginAndCode() {
        val address = Connection.publicAddress("k7q4m-2p8wx")
        val connection = Connection.create(address, "", false)
        assertTrue(connection.hosted)
        assertFalse(connection.requiresToken)
        assertEquals(address + "/latest", connection.latestUrl)
        for (bad in listOf("https://evil.example/api/channels/K7Q4M2P8WX", Connection.PUBLIC_ORIGIN + "/api/channels/../../latest",
            Connection.PUBLIC_ORIGIN + "/api/channels/K7Q4M2P8WX?x=1", address.replace("https:", "http:"))) {
            assertThrows(IllegalArgumentException::class.java) { Connection.create(bad, "", true) }
        }
    }
    private val token = "d".repeat(64) // Disposable test value; never used by the app/server.
    @Test fun emulatorAndLanAddressesHaveDistinctTokenRules() {
        assertFalse(Connection.create(" http://10.0.2.2:4180/ ", "", true).requiresToken)
        val lan = Connection.create("http://192.168.1.23:4180", token, true)
        assertTrue(lan.requiresToken); assertFalse(lan.toString().contains(token))
        assertThrows(IllegalArgumentException::class.java) { Connection.create("http://192.168.1.23:4180", "", true) }
    }
    @Test fun rejectsPublicDnsCredentialAndRedirectLikeAddresses() {
        for (address in listOf("http://8.8.8.8", "http://example.com", "http://192.168.1.1/path",
            "http://user:pass@127.0.0.1", "http://127.0.0.1?token=x", "file:///tmp/a",
            "http://127.0.0.1:99999", "http://010.0.0.1", "http://127.0.0.1#x")) {
            assertThrows(address, IllegalArgumentException::class.java) { Connection.create(address, token, true) }
        }
    }
    @Test fun releaseRejectsHttpAndTokenHeadersRejectControlCharacters() {
        assertThrows(IllegalArgumentException::class.java) { Connection.create(Connection.EMULATOR_URL, "", false) }
        assertEquals("https://127.0.0.1:4180", Connection.create("https://127.0.0.1:4180", "", false).baseUrl)
        assertThrows(IllegalArgumentException::class.java) { Connection.create("http://192.168.0.2:4180", token+"\r\nX-Header: x", true) }
    }
}
