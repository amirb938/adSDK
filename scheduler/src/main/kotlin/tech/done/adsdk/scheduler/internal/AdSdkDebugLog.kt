package tech.done.adsdk.scheduler.internal

internal object AdSdkDebugLog {
    @Volatile
    var enabled: Boolean = System.getProperty("adsdk.debug") == "true"

    fun d(tag: String, msg: String) {
        if (!enabled) return
        println("AdSDK/$tag D $msg")
    }
}

