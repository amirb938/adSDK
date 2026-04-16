package tech.done.ads.sample

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.done.ads.AdSdkLogConfig
import tech.done.ads.network.NetworkLayer
import tech.done.ads.network.NetworkResponse
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

class SampleNetworkLayer(
    private val context: Context,
    override val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NetworkLayer {

    override suspend fun get(
        url: String,
        timeoutMs: Long?,
        headers: Map<String, String>,
    ): NetworkResponse = withContext(dispatcher) {
        if (AdSdkLogConfig.isDebugLoggingEnabled) {
            Timber.tag(TAG).d("GET url=$url timeoutMs=$timeoutMs headers=${headers.keys.sorted()}")
        }
        if (url.startsWith("asset://")) {
            val assetName = url.removePrefix("asset://")
            val body = context.assets.open(assetName).bufferedReader().use { it.readText() }
            if (AdSdkLogConfig.isDebugLoggingEnabled) {
                Timber.tag(TAG).d("200 asset=$assetName bytes=${body.length}")
            }
            return@withContext NetworkResponse(code = 200, body = body)
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = (timeoutMs ?: 10_000L).toInt()
                readTimeout = (timeoutMs ?: 10_000L).toInt()
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            return@withContext try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.use { it.bufferedReader().use { r -> r.readText() } }
                if (AdSdkLogConfig.isDebugLoggingEnabled) {
                    Timber.tag(TAG).d("$code bytes=${body?.length ?: 0}")
                }
                NetworkResponse(code = code, body = body, headers = emptyMap())
            } catch (t: Throwable) {
                if (AdSdkLogConfig.isDebugLoggingEnabled) {
                    Timber.tag(TAG).e(t, "fetch failed url=$url")
                }
                NetworkResponse(code = 599, body = null)
            } finally {
                runCatching { conn.disconnect() }
            }
        }

        if (AdSdkLogConfig.isDebugLoggingEnabled) {
            Timber.tag(TAG).w("204 (unsupported scheme)")
        }
        return@withContext NetworkResponse(code = 204, body = null)
    }

    private companion object {
        private const val TAG = "AdSDK/Network(Sample)"
    }
}
