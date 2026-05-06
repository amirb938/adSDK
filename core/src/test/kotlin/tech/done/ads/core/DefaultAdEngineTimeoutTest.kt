package tech.done.ads.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultAdEngineTimeoutTest {

    @Test
    fun `non-SIMID uses wait plus buffer`() {
        val result = computeMaxWallClockMs(
            waitMs = 32_000L,
            bufferTimeoutMs = 15_000L,
            isSimid = false,
            simidMaxWallClockMs = 300_000L,
        )

        assertEquals(47_000L, result)
    }

    @Test
    fun `SIMID uses at least configured SIMID wall clock`() {
        val result = computeMaxWallClockMs(
            waitMs = 32_000L,
            bufferTimeoutMs = 15_000L,
            isSimid = true,
            simidMaxWallClockMs = 300_000L,
        )

        assertEquals(300_000L, result)
    }

    @Test
    fun `SIMID keeps larger default wall clock when already bigger`() {
        val result = computeMaxWallClockMs(
            waitMs = 400_000L,
            bufferTimeoutMs = 20_000L,
            isSimid = true,
            simidMaxWallClockMs = 300_000L,
        )

        assertEquals(420_000L, result)
    }
}
