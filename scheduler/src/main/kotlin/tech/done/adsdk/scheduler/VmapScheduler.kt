package tech.done.adsdk.scheduler

import tech.done.adsdk.parser.model.Position
import tech.done.adsdk.parser.model.VmapResponse
import tech.done.adsdk.scheduler.internal.AdSdkDebugLog

class VmapScheduler : AdScheduler {
    override fun buildTimeline(vmap: VmapResponse): AdTimeline {
        val logTag = "Scheduler"
        AdSdkDebugLog.d(logTag, "buildTimeline version=${vmap.version} breaks=${vmap.adBreaks.size}")
        val preroll = mutableListOf<ScheduledBreak>()
        val midrolls = mutableListOf<ScheduledBreak>()
        val postroll = mutableListOf<ScheduledBreak>()

        for (adBreak in vmap.adBreaks) {
            AdSdkDebugLog.d(
                logTag,
                "break breakId=${adBreak.breakId} pos=${adBreak.position} timeOffsetMs=${adBreak.timeOffsetMs} vastAdTagUri=${adBreak.vastAdTagUri} inlineBytes=${adBreak.vastInlineXml?.length}",
            )
            val scheduled = ScheduledBreak(
                breakId = adBreak.breakId,
                original = adBreak,
                triggerTimeMs = when (adBreak.position) {
                    Position.Preroll -> 0L
                    Position.Midroll -> adBreak.timeOffsetMs
                    Position.Postroll -> null
                },
                vastAdTagUri = adBreak.vastAdTagUri,
                vastInlineXml = adBreak.vastInlineXml,
            )

            // Ignore empty breaks (no VAST URL and no inline VAST) at scheduler stage.
            if (scheduled.vastAdTagUri.isNullOrBlank() && scheduled.vastInlineXml.isNullOrBlank()) {
                AdSdkDebugLog.d(logTag, "skip breakId=${scheduled.breakId} reason=noVastSource")
                continue
            }

            when (adBreak.position) {
                Position.Preroll -> preroll += scheduled
                Position.Midroll -> {
                    // Time-based midroll only; malformed offsets are ignored.
                    if (scheduled.triggerTimeMs != null && scheduled.triggerTimeMs >= 0) {
                        midrolls += scheduled
                    } else {
                        AdSdkDebugLog.d(
                            logTag,
                            "skip breakId=${scheduled.breakId} reason=invalidMidrollOffset triggerTimeMs=${scheduled.triggerTimeMs}",
                        )
                    }
                }
                Position.Postroll -> postroll += scheduled
            }
        }

        AdSdkDebugLog.d(logTag, "timeline built preroll=${preroll.size} midroll=${midrolls.size} postroll=${postroll.size}")
        return AdTimeline(
            preroll = preroll,
            midrolls = midrolls.sortedBy { it.triggerTimeMs ?: Long.MAX_VALUE },
            postroll = postroll,
        )
    }
}

