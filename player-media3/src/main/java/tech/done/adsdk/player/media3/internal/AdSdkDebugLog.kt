package tech.done.adsdk.player.media3.internal

internal object AdSdkDebugLog {
    @Volatile
    var enabled: Boolean = System.getProperty("adsdk.debug") == "true"

    fun d(tag: String, msg: String) {
        if (!enabled) return
        println("AdSDK/$tag D $msg")
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (!enabled) return
        println("AdSDK/$tag E $msg" + (t?.let { " :: ${it::class.simpleName}: ${it.message}" } ?: ""))
        if (t != null) t.printStackTrace()
    }
}

