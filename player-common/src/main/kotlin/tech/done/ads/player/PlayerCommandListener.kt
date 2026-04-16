package tech.done.ads.player

interface PlayerCommandListener {
    fun onPlayAdRequested(mediaUri: String, adSkipOffsetMs: Long? = null, simidInteractiveCreativeUrl: String? = null)
    fun onResumeContentRequested()
    fun onPauseRequested()
    fun onPlayRequested()
    fun onSeekingEnabledChanged(enabled: Boolean)
}

