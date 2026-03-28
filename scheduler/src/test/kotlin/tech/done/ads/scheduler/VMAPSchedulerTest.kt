package tech.done.ads.scheduler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tech.done.ads.parser.model.AdBreak
import tech.done.ads.parser.model.Position
import tech.done.ads.parser.model.VMAPResponse

class VMAPSchedulerTest {

    private val scheduler = VMAPScheduler()

    @Test
    fun `preroll midroll postroll buckets`() {
        val vmap = VMAPResponse(
            version = "1.0",
            adBreaks = listOf(
                AdBreak("p", Position.Preroll, 0L, "https://p", null),
                AdBreak("m", Position.Midroll, 60_000L, "https://m", null),
                AdBreak("po", Position.Postroll, null, "https://po", null),
            ),
        )
        val tl = scheduler.buildTimeline(vmap)
        assertEquals(1, tl.preroll.size)
        assertEquals(1, tl.midrolls.size)
        assertEquals(1, tl.postroll.size)
        assertTrue(tl.midrolls.single().triggerTimeMs == 60_000L)
    }

    @Test
    fun `empty VAST source skipped`() {
        val vmap = VMAPResponse(
            adBreaks = listOf(
                AdBreak("x", Position.Preroll, 0L, null, null),
            ),
        )
        val tl = scheduler.buildTimeline(vmap)
        assertTrue(tl.preroll.isEmpty())
    }
}
