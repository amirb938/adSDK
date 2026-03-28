package tech.done.ads.scheduler.internal

import tech.done.ads.AdSdkLogConfig
import timber.log.Timber

internal object AdSdkDebugLog {
    private fun tree(tag: String): Timber.Tree = Timber.tag("AdSDK/$tag")

    fun d(tag: String, msg: String) {
        if (!AdSdkLogConfig.isDebugLoggingEnabled) return
        tree(tag).d(msg)
    }
}
