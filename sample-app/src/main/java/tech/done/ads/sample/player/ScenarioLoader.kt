package tech.done.ads.sample.player

import kotlinx.coroutines.flow.StateFlow
import tech.done.ads.player.PlayerState

interface ScenarioLoader {
    val playerState: StateFlow<PlayerState>
    fun addLogger()
    fun requestAdsFromVMAPXml(xml: String)
    fun start()
    fun skipCurrentAd()
    fun release()
}

