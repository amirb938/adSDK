package tech.done.ads.network

import kotlinx.coroutines.CoroutineDispatcher


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

