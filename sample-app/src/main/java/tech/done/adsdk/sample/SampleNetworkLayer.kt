package tech.done.adsdk.sample

import android.content.Context
import tech.done.adsdk.network.NetworkLayer
import tech.done.adsdk.network.NetworkResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        // Local "network" for sample: asset://<name>.xml
        if (url.startsWith("asset://")) {
            val assetName = url.removePrefix("asset://")
            val body = context.assets.open(assetName).bufferedReader().use { it.readText() }
            if (System.getProperty("adsdk.debug") == "true") {
                println("AdSDK/Network(Sample) D 200 asset=$assetName bytes=${body.length}")
            }
            return@withContext NetworkResponse(code = 200, body = body)
        }

        // For demo video media URIs etc: do not fetch bodies here.
        if (System.getProperty("adsdk.debug") == "true") {
            println("AdSDK/Network(Sample) W 204 (no fetch implementation for this url)")
        }
        return@withContext NetworkResponse(code = 204, body = null)
    }
}

