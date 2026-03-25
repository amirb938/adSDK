package tech.done.adsdk.core.internal

/**
 * Lightweight logging for debugging issues in apps without adding Android dependencies.
 *
 * Enable by setting `System.setProperty("adsdk.debug", "true")` early in app startup.
 */
internal object AdSdkDebugLog {
    @Volatile
    var enabled: Boolean = System.getProperty("adsdk.debug") == "true"

    fun d(tag: String, msg: String) {
        if (!enabled) return
        println("AdSDK/$tag D $msg")
    }

    fun w(tag: String, msg: String) {
        if (!enabled) return
        println("AdSDK/$tag W $msg")
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (!enabled) return
        println("AdSDK/$tag E $msg" + (t?.let { " :: ${it::class.simpleName}: ${it.message}" } ?: ""))
        if (t != null) {
            t.printStackTrace()
        }
    }
}

