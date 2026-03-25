package com.example.adsdk.scheduler

import com.example.adsdk.parser.model.Position
import com.example.adsdk.parser.model.VmapResponse

class VmapScheduler : AdScheduler {
    override fun buildTimeline(vmap: VmapResponse): AdTimeline {
        val preroll = mutableListOf<ScheduledBreak>()
        val midrolls = mutableListOf<ScheduledBreak>()
        val postroll = mutableListOf<ScheduledBreak>()

        for (adBreak in vmap.adBreaks) {
            val scheduled = ScheduledBreak(
                breakId = adBreak.breakId,
                original = adBreak,
                triggerTimeMs = when (adBreak.position) {
                    Position.Preroll -> 0L
                    Position.Midroll -> adBreak.timeOffsetMs
                    Position.Postroll -> null
                },
                vastAdTagUri = adBreak.vastAdTagUri,
            )

            // Ignore empty breaks (no VAST tag URI) at scheduler stage.
            if (scheduled.vastAdTagUri.isNullOrBlank()) continue

            when (adBreak.position) {
                Position.Preroll -> preroll += scheduled
                Position.Midroll -> {
                    // Time-based midroll only; malformed offsets are ignored.
                    if (scheduled.triggerTimeMs != null && scheduled.triggerTimeMs >= 0) {
                        midrolls += scheduled
                    }
                }
                Position.Postroll -> postroll += scheduled
            }
        }

        return AdTimeline(
            preroll = preroll,
            midrolls = midrolls.sortedBy { it.triggerTimeMs ?: Long.MAX_VALUE },
            postroll = postroll,
        )
    }
}

