package tech.done.ads.player.media3

import androidx.media3.ui.PlayerView
import tech.done.ads.player.PlayerCommandListener

class ExternalPlayerControllerHidingCommandListener(
    private val delegate: PlayerCommandListener,
    private val playerViewProvider: () -> PlayerView?,
) : PlayerCommandListener {
    override fun onPlayAdRequested(
        mediaUri: String,
        adSkipOffsetMs: Long?,
        simidInteractiveCreativeUrl: String?,
    ) {
        ExternalPlayerViewControllerPolicy.apply(playerViewProvider(), inAd = true)
        delegate.onPlayAdRequested(mediaUri, adSkipOffsetMs, simidInteractiveCreativeUrl)
    }

    override fun onResumeContentRequested() {
        ExternalPlayerViewControllerPolicy.apply(playerViewProvider(), inAd = false)
        delegate.onResumeContentRequested()
    }

    override fun onPauseRequested() {
        delegate.onPauseRequested()
    }

    override fun onPlayRequested() {
        delegate.onPlayRequested()
    }

    override fun onSeekingEnabledChanged(enabled: Boolean) {
        delegate.onSeekingEnabledChanged(enabled)
    }
}

