package tech.done.ads.player

interface AdsEventListener {
    fun onVMAPParsed(version: String?, adBreakCount: Int) {}

    fun onAdBreakLoading(breakId: String?, triggerTimeMs: Long?) {}

    fun onContentPauseRequested(breakId: String?) {}

    fun onVASTLoaded(breakId: String?, durationMs: Long?, mediaUri: String?) {}

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

    fun onContentResumeRequested(breakId: String?) {}

    fun onAdError(breakId: String?, error: Throwable?) {}
}
