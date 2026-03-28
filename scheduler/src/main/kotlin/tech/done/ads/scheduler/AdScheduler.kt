package tech.done.ads.scheduler

import tech.done.ads.parser.model.AdBreak
import tech.done.ads.parser.model.VMAPResponse

interface AdScheduler {
    fun buildTimeline(vmap: VMAPResponse): AdTimeline
}

data class AdTimeline(
    val preroll: List<ScheduledBreak> = emptyList(),
    val midrolls: List<ScheduledBreak> = emptyList(),
    val postroll: List<ScheduledBreak> = emptyList(),
)

data class ScheduledBreak(
    val breakId: String?,
    val original: AdBreak,
    val triggerTimeMs: Long?,
    val vastAdTagUri: String?,
    val vastInlineXml: String?,
)

