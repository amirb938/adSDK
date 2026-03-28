package tech.done.ads.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdStatePhaseTest {

    @Test
    fun phasesDistinct() {
        assertEquals(AdState.Phase.Initialized, AdState.Phase.Initialized)
        assertEquals(6, AdState.Phase.entries.size)
    }
}
