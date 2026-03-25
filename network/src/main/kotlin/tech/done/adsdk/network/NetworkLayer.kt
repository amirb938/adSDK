package tech.done.adsdk.network

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Network abstraction for the SDK.
 *
 * Pure Kotlin. Concrete implementations can use OkHttp/Ktor/etc in Android modules.
 */
interface NetworkLayer {
    val dispatcher: CoroutineDispatcher

    suspend fun get(url: String, timeoutMs: Long? = null, headers: Map<String, String> = emptyMap()): NetworkResponse
}

data class NetworkResponse(
    val code: Int,
    val body: String?,
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccessful: Boolean get() = code in 200..299
}

