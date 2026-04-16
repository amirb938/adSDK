package tech.done.ads.player

import org.json.JSONObject

interface InteractiveAdBridge {
    fun setListener(listener: Listener?)

    fun sendToCreative(type: String, sessionId: String? = null, args: JSONObject? = null)

    interface Listener {
        fun onMessageFromCreative(type: String, sessionId: String? = null, args: JSONObject? = null)
    }
}

