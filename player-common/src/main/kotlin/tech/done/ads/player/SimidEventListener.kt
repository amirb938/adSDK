package tech.done.ads.player

import org.json.JSONObject

interface SimidEventListener {
    fun onSimidReady(sessionId: String)

    fun onSimidAction(sessionId: String, type: String, args: JSONObject?)
}
