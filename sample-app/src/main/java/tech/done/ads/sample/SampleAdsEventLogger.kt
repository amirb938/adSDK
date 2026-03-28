package tech.done.ads.sample

import tech.done.ads.player.AdsEventListener
import timber.log.Timber

class SampleAdsEventLogger : AdsEventListener {

    private val tag = "AdSDK/IMA-Events"

    override fun onVMAPParsed(version: String?, adBreakCount: Int) {
        Timber.tag(tag).d("onVMAPParsed version=%s breaks=%d", version, adBreakCount)
    }

    override fun onAdBreakLoading(breakId: String?, triggerTimeMs: Long?) {
        Timber.tag(tag).d("onAdBreakLoading breakId=%s triggerTimeMs=%s", breakId, triggerTimeMs)
    }

    override fun onContentPauseRequested(breakId: String?) {
        Timber.tag(tag).d("onContentPauseRequested breakId=%s", breakId)
    }

    override fun onVASTLoaded(breakId: String?, durationMs: Long?, mediaUri: String?) {
        Timber.tag(tag).d("onVASTLoaded breakId=%s durationMs=%s uri=%s", breakId, durationMs, mediaUri)
    }

    override fun onAdImpression(breakId: String?) {
        Timber.tag(tag).d("onAdImpression breakId=%s", breakId)
    }

    override fun onAdStarted(breakId: String?) {
        Timber.tag(tag).d("onAdStarted breakId=%s", breakId)
    }

    override fun onAdProgress(breakId: String?, positionMs: Long, durationMs: Long?) {
        Timber.tag(tag).v("onAdProgress breakId=%s pos=%d dur=%s", breakId, positionMs, durationMs)
    }

    override fun onAdFirstQuartile(breakId: String?) {
        Timber.tag(tag).d("onAdFirstQuartile breakId=%s", breakId)
    }

    override fun onAdMidpoint(breakId: String?) {
        Timber.tag(tag).d("onAdMidpoint breakId=%s", breakId)
    }

    override fun onAdThirdQuartile(breakId: String?) {
        Timber.tag(tag).d("onAdThirdQuartile breakId=%s", breakId)
    }

    override fun onAdPaused(breakId: String?) {
        Timber.tag(tag).d("onAdPaused breakId=%s", breakId)
    }

    override fun onAdResumed(breakId: String?) {
        Timber.tag(tag).d("onAdResumed breakId=%s", breakId)
    }

    override fun onAdSkipped(breakId: String?) {
        Timber.tag(tag).d("onAdSkipped breakId=%s", breakId)
    }

    override fun onAdCompleted(breakId: String?) {
        Timber.tag(tag).d("onAdCompleted breakId=%s", breakId)
    }

    override fun onContentResumeRequested(breakId: String?) {
        Timber.tag(tag).d("onContentResumeRequested breakId=%s", breakId)
    }

    override fun onAdError(breakId: String?, error: Throwable?) {
        Timber.tag(tag).e(error, "onAdError breakId=%s", breakId)
    }
}
