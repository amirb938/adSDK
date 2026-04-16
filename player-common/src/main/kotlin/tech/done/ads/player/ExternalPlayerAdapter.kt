package tech.done.ads.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExternalPlayerAdapter(
    private val commands: PlayerCommandListener,
) : PlayerAdapter {
    private val listeners = LinkedHashSet<PlayerListener>()

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    override fun addListener(listener: PlayerListener) {
        listeners += listener
    }

    override fun removeListener(listener: PlayerListener) {
        listeners -= listener
    }

    override fun setSeekingEnabled(enabled: Boolean) {
        commands.onSeekingEnabledChanged(enabled)
    }

    override fun playAd(mediaUri: String, adSkipOffsetMs: Long?, simidInteractiveCreativeUrl: String?) {
        commands.onPlayAdRequested(mediaUri, adSkipOffsetMs, simidInteractiveCreativeUrl)
    }

    override fun resumeContent() {
        commands.onResumeContentRequested()
    }

    override fun pause() {
        commands.onPauseRequested()
    }

    override fun play() {
        commands.onPlayRequested()
    }

    fun updateState(state: PlayerState) {
        _state.value = state
    }

    fun notifyContentEnded() {
        listeners.forEach { it.onContentEnded() }
    }

    fun notifyAdEnded() {
        listeners.forEach { it.onAdEnded() }
    }

    fun notifyAppBackgrounded() {
        listeners.forEach { it.onAppBackgrounded() }
    }

    fun notifyAppForegrounded() {
        listeners.forEach { it.onAppForegrounded() }
    }

    fun notifyAdProgress(positionMs: Long, durationMs: Long?) {
        listeners.forEach { it.onAdProgress(positionMs, durationMs) }
    }

    fun notifyPlayerError(throwable: Throwable) {
        listeners.forEach { it.onPlayerError(throwable) }
    }
}

