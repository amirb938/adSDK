package tech.done.ads.parser.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TimeParsingTest {

    @Test
    fun parseVASTTimeToMs_hhMmSs() {
        assertEquals(90_000L, parseVASTTimeToMs("00:01:30"))
        assertEquals(90_500L, parseVASTTimeToMs("00:01:30.500"))
    }

    @Test
    fun parseVASTTimeToMs_invalid() {
        assertNull(parseVASTTimeToMs(""))
        assertNull(parseVASTTimeToMs("bad"))
    }

    @Test
    fun parseVMAPTimeOffsetToMs() {
        assertEquals(0L, parseVMAPTimeOffsetToMs("00:00:00"))
        assertEquals(3_600_000L, parseVMAPTimeOffsetToMs("01:00:00"))
    }
}
