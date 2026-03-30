package tech.done.ads.player.media3.ima

import org.json.JSONObject

data class SimidMessage(
    val sessionId: String,
    val messageId: Int,
    val timestamp: Long,
    val type: String,
    val args: JSONObject?,
)
