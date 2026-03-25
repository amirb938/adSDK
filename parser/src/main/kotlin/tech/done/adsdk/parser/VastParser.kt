package tech.done.adsdk.parser

import tech.done.adsdk.parser.model.VastAd

/**
 * VAST parsing contract.
 *
 * Pure Kotlin: no Android framework dependencies.
 */
interface VastParser {
    suspend fun parse(xml: String, wrapperFetcher: VastWrapperFetcher? = null): List<VastAd>
}

/**
 * Pluggable wrapper XML fetcher. Keep this in the parser module (pure Kotlin),
 * so network implementations can live elsewhere.
 */
fun interface VastWrapperFetcher {
    suspend fun fetchXml(url: String): String
}
