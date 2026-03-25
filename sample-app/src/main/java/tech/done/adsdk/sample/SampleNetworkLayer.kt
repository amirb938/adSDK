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
        // Local "network" for sample: asset://<name>.xml
        if (url.startsWith("asset://")) {
            val assetName = url.removePrefix("asset://")
            val body = context.assets.open(assetName).bufferedReader().use { it.readText() }
            return@withContext NetworkResponse(code = 200, body = body)
        }

        // For demo video media URIs etc: do not fetch bodies here.
        return@withContext NetworkResponse(code = 204, body = null)
    }
}

