package tech.done.ads.parser

import tech.done.ads.parser.model.VASTAd


interface VASTParser {
    suspend fun parse(xml: String, wrapperFetcher: VASTWrapperFetcher? = null): List<VASTAd>
}


fun interface VASTWrapperFetcher {
    suspend fun fetchXml(url: String): String
}
