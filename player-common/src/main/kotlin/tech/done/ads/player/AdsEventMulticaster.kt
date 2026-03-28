package tech.done.ads.player

import java.util.concurrent.CopyOnWriteArraySet

class AdsEventMulticaster : AdsEventListener {
    private val listeners = CopyOnWriteArraySet<AdsEventListener>()

    fun addListener(listener: AdsEventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: AdsEventListener) {
        listeners.remove(listener)
    }

    fun clear() {
        listeners.clear()
    }

    override fun onVMAPParsed(version: String?, adBreakCount: Int) {
        listeners.forEach { it.onVMAPParsed(version, adBreakCount) }
    }

    override fun onAdBreakLoading(breakId: String?, triggerTimeMs: Long?) {
        listeners.forEach { it.onAdBreakLoading(breakId, triggerTimeMs) }
    }

    override fun onContentPauseRequested(breakId: String?) {
        listeners.forEach { it.onContentPauseRequested(breakId) }
    }

    override fun onVASTLoaded(breakId: String?, durationMs: Long?, mediaUri: String?) {
        listeners.forEach { it.onVASTLoaded(breakId, durationMs, mediaUri) }
    }

    override fun onAdImpression(breakId: String?) {
        listeners.forEach { it.onAdImpression(breakId) }
    }

    override fun onAdStarted(breakId: String?) {
        listeners.forEach { it.onAdStarted(breakId) }
    }

    override fun onAdProgress(breakId: String?, positionMs: Long, durationMs: Long?) {
        listeners.forEach { it.onAdProgress(breakId, positionMs, durationMs) }
    }

    override fun onAdFirstQuartile(breakId: String?) {
        listeners.forEach { it.onAdFirstQuartile(breakId) }
    }

    override fun onAdMidpoint(breakId: String?) {
        listeners.forEach { it.onAdMidpoint(breakId) }
    }

    override fun onAdThirdQuartile(breakId: String?) {
        listeners.forEach { it.onAdThirdQuartile(breakId) }
    }

    override fun onAdPaused(breakId: String?) {
        listeners.forEach { it.onAdPaused(breakId) }
    }

    override fun onAdResumed(breakId: String?) {
        listeners.forEach { it.onAdResumed(breakId) }
    }

    override fun onAdSkipped(breakId: String?) {
        listeners.forEach { it.onAdSkipped(breakId) }
    }

    override fun onAdCompleted(breakId: String?) {
        listeners.forEach { it.onAdCompleted(breakId) }
    }

    override fun onContentResumeRequested(breakId: String?) {
        listeners.forEach { it.onContentResumeRequested(breakId) }
    }

    override fun onAdError(breakId: String?, error: Throwable?) {
        listeners.forEach { it.onAdError(breakId, error) }
    }
}
