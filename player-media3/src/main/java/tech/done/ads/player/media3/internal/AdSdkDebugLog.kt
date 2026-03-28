package tech.done.ads.player.media3.internal

import tech.done.ads.AdSdkLogConfig
import timber.log.Timber

internal object AdSdkDebugLog {
    private fun tree(tag: String): Timber.Tree = Timber.tag("AdSDK/$tag")

    fun d(tag: String, msg: String) {
        if (!AdSdkLogConfig.isDebugLoggingEnabled) return
        tree(tag).d(msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (!AdSdkLogConfig.isDebugLoggingEnabled) return
        if (t != null) {
            tree(tag).e(t, msg)
        } else {
            tree(tag).e(msg)
        }
    }
}
