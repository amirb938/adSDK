package tech.done.adsdk.core

/**
 * Core orchestration contract for ad playback lifecycle.
 *
 * Pure Kotlin: must not depend on Android framework types.
 */
interface AdEngine {
    suspend fun loadVmap(xml: String)
    fun initialize()
    fun start()
    fun stop()
    fun release()

    val state: AdState
}

