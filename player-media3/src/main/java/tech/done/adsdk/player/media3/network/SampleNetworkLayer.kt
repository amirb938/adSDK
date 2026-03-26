package tech.done.adsdk.player.media3.network

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.done.adsdk.network.NetworkLayer
import tech.done.adsdk.network.NetworkResponse
import java.net.HttpURLConnection
import java.net.URL

/**
 * A small reference implementation used by the sample app and as a default for [Media3AdsLoader].
 *
 * Supports:
 * - asset://<name>.xml (Android assets)
 * - http(s)://...
 */
class SampleNetworkLayer(
    private val context: Context,
    override val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NetworkLayer {

    override suspend fun get(
        url: String,
        timeoutMs: Long?,
        headers: Map<String, String>,
    ): NetworkResponse = withContext(dispatcher) {
        if (System.getProperty("adsdk.debug") == "true") {
            println("AdSDK/Network(Sample) D GET url=$url timeoutMs=$timeoutMs headers=${headers.keys.sorted()}")
        }

        if (url.startsWith("asset://")) {
            val assetName = url.removePrefix("asset://")
            val body = context.assets.open(assetName).bufferedReader().use { it.readText() }
            if (System.getProperty("adsdk.debug") == "true") {
                println("AdSDK/Network(Sample) D 200 asset=$assetName bytes=${body.length}")
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
                val body = stream?.bufferedReader()?.use { it.readText() }
                if (System.getProperty("adsdk.debug") == "true") {
                    println("AdSDK/Network(Sample) D $code bytes=${body?.length ?: 0}")
                }
                NetworkResponse(code = code, body = body, headers = emptyMap())
            } catch (t: Throwable) {
                if (System.getProperty("adsdk.debug") == "true") {
                    println("AdSDK/Network(Sample) E fetch failed url=$url err=${t.message}")
                }
                NetworkResponse(code = 599, body = null)
            } finally {
                runCatching { conn.disconnect() }
            }
        }

        if (System.getProperty("adsdk.debug") == "true") {
            println("AdSDK/Network(Sample) W 204 (unsupported scheme)")
        }
        NetworkResponse(code = 204, body = null)
    }
}

