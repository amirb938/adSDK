package tech.done.ads.player

interface PlayerListener {
    fun onContentEnded() {}

    fun onAdEnded() {}

    fun onAppBackgrounded() {}

    fun onAppForegrounded() {}

    fun onAdProgress(positionMs: Long, durationMs: Long?) {}

    fun onPlayerError(throwable: Throwable) {}
}

