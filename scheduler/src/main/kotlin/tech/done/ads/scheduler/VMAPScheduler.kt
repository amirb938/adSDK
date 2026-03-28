package tech.done.ads.scheduler

import tech.done.ads.parser.model.Position
import tech.done.ads.parser.model.VMAPResponse
import tech.done.ads.scheduler.internal.AdSdkDebugLog

class VMAPScheduler : AdScheduler {
    override fun buildTimeline(vmap: VMAPResponse): AdTimeline {
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

            if (scheduled.vastAdTagUri.isNullOrBlank() && scheduled.vastInlineXml.isNullOrBlank()) {
                AdSdkDebugLog.d(logTag, "skip breakId=${scheduled.breakId} reason=noVASTSource")
                continue
            }

            when (adBreak.position) {
                Position.Preroll -> preroll += scheduled
                Position.Midroll -> {
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

