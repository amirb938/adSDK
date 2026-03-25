package tech.done.adsdk.player

interface PlayerListener {
    fun onContentEnded() {}

    /**
     * Called when the currently playing ad media reaches end of stream.
     * This is the main signal the engine uses to know when to resume content.
     */
    fun onAdEnded() {}

    /**
     * Called when app moves to background (or playback becomes non-interactive).
     */
    fun onAppBackgrounded() {}

    fun onAppForegrounded() {}

    /**
     * Optional ad progress signal to enable skip at the correct time.
     */
    fun onAdProgress(positionMs: Long, durationMs: Long?) {}

    fun onPlayerError(throwable: Throwable) {}
}

