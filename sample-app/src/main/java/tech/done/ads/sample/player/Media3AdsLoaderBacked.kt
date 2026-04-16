package tech.done.ads.sample.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import tech.done.ads.player.media3.ima.AdDisplayContainerView

interface Media3AdsLoaderBacked : ScenarioLoader {
    fun attachPlayer(player: ExoPlayer, playerView: PlayerView)
    fun attachContainer(container: AdDisplayContainerView)
}

