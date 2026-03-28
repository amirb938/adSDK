package tech.done.ads.core

interface AdEngine {
    suspend fun loadVMAP(xml: String)
    fun initialize()
    fun start()
    fun stop()
    fun release()

    val state: AdState
}

