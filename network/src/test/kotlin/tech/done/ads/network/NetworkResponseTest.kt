package tech.done.ads.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkResponseTest {

    @Test
    fun isSuccessful() {
        assertTrue(NetworkResponse(200, "").isSuccessful)
        assertFalse(NetworkResponse(404, null).isSuccessful)
        assertEquals("body", NetworkResponse(200, "body").body)
    }
}
