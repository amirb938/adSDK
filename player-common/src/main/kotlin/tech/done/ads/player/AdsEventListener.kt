package tech.done.ads.player

interface AdsSchedulingListener {
    fun onVMAPParsed(version: String?, adBreakCount: Int) {}

    fun onAdBreakLoading(breakId: String?, triggerTimeMs: Long?) {}

    fun onContentPauseRequested(breakId: String?) {}

    fun onVASTLoaded(breakId: String?, durationMs: Long?, mediaUri: String?) {}

    fun onContentResumeRequested(breakId: String?) {}

    fun onAdError(breakId: String?, error: Throwable?) {}
}

interface AdsCreativePlaybackListener {
    fun onAdImpression(breakId: String?) {}

    fun onAdStarted(breakId: String?) {}

    fun onAdProgress(breakId: String?, positionMs: Long, durationMs: Long?) {}

    fun onAdFirstQuartile(breakId: String?) {}

    fun onAdMidpoint(breakId: String?) {}

    fun onAdThirdQuartile(breakId: String?) {}

    fun onAdPaused(breakId: String?) {}

    fun onAdResumed(breakId: String?) {}

    fun onAdSkipped(breakId: String?) {}

    fun onAdCompleted(breakId: String?) {}
}

interface AdsEventListener : AdsSchedulingListener, AdsCreativePlaybackListener

enum class AdsEventDomain {
    SchedulingAndBreaks,
    CreativePlayback,
}

enum class AdsEventKind {
    VMAP_PARSED,
    AD_BREAK_LOADING,
    CONTENT_PAUSE_REQUESTED,
    VAST_LOADED,
    AD_IMPRESSION,
    AD_STARTED,
    AD_PROGRESS,
    AD_FIRST_QUARTILE,
    AD_MIDPOINT,
    AD_THIRD_QUARTILE,
    AD_PAUSED,
    AD_RESUMED,
    AD_SKIPPED,
    AD_COMPLETED,
    CONTENT_RESUME_REQUESTED,
    AD_ERROR,
}

fun AdsEventKind.domain(): AdsEventDomain = when (this) {
    AdsEventKind.VMAP_PARSED,
    AdsEventKind.AD_BREAK_LOADING,
    AdsEventKind.CONTENT_PAUSE_REQUESTED,
    AdsEventKind.VAST_LOADED,
    AdsEventKind.CONTENT_RESUME_REQUESTED,
    AdsEventKind.AD_ERROR,
        -> AdsEventDomain.SchedulingAndBreaks

    AdsEventKind.AD_IMPRESSION,
    AdsEventKind.AD_STARTED,
    AdsEventKind.AD_PROGRESS,
    AdsEventKind.AD_FIRST_QUARTILE,
    AdsEventKind.AD_MIDPOINT,
    AdsEventKind.AD_THIRD_QUARTILE,
    AdsEventKind.AD_PAUSED,
    AdsEventKind.AD_RESUMED,
    AdsEventKind.AD_SKIPPED,
    AdsEventKind.AD_COMPLETED,
        -> AdsEventDomain.CreativePlayback
}

data class AdsEventPayload(
    val version: String? = null,
    val adBreakCount: Int = 0,
    val triggerTimeMs: Long? = null,
    val durationMs: Long? = null,
    val mediaUri: String? = null,
    val positionMs: Long = 0L,
    val error: Throwable? = null,
)

fun dispatchAdsEvent(
    listener: AdsEventListener?,
    kind: AdsEventKind,
    breakId: String?,
    payload: AdsEventPayload = AdsEventPayload(),
) {
    val l = listener ?: return
    when (kind) {
        AdsEventKind.VMAP_PARSED -> l.onVMAPParsed(payload.version, payload.adBreakCount)
        AdsEventKind.AD_BREAK_LOADING -> l.onAdBreakLoading(breakId, payload.triggerTimeMs)
        AdsEventKind.CONTENT_PAUSE_REQUESTED -> l.onContentPauseRequested(breakId)
        AdsEventKind.VAST_LOADED -> l.onVASTLoaded(breakId, payload.durationMs, payload.mediaUri)
        AdsEventKind.AD_IMPRESSION -> l.onAdImpression(breakId)
        AdsEventKind.AD_STARTED -> l.onAdStarted(breakId)
        AdsEventKind.AD_PROGRESS -> l.onAdProgress(breakId, payload.positionMs, payload.durationMs)
        AdsEventKind.AD_FIRST_QUARTILE -> l.onAdFirstQuartile(breakId)
        AdsEventKind.AD_MIDPOINT -> l.onAdMidpoint(breakId)
        AdsEventKind.AD_THIRD_QUARTILE -> l.onAdThirdQuartile(breakId)
        AdsEventKind.AD_PAUSED -> l.onAdPaused(breakId)
        AdsEventKind.AD_RESUMED -> l.onAdResumed(breakId)
        AdsEventKind.AD_SKIPPED -> l.onAdSkipped(breakId)
        AdsEventKind.AD_COMPLETED -> l.onAdCompleted(breakId)
        AdsEventKind.CONTENT_RESUME_REQUESTED -> l.onContentResumeRequested(breakId)
        AdsEventKind.AD_ERROR -> l.onAdError(breakId, payload.error)
    }
}
