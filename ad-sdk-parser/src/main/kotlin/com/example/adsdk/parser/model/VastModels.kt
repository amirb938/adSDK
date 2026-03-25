package com.example.adsdk.parser.model

/**
 * Lightweight data models to decouple the SDK from raw XML formats.
 * These are intentionally minimal in the skeleton stage.
 */
data class VastDocument(
    val version: String? = null,
    val ads: List<VastAd> = emptyList(),
)

data class VastAd(
    val id: String? = null,
    val sequence: Int? = null,
    val inline: VastInline? = null,
    val wrappers: List<VastWrapper> = emptyList(),
)

data class VastInline(
    val adSystem: String? = null,
    val adTitle: String? = null,
    val impressions: List<String> = emptyList(),
    val creatives: List<VastCreative> = emptyList(),
)

data class VastWrapper(
    val vastAdTagUri: String? = null,
    val impressions: List<String> = emptyList(),
)

data class VastCreative(
    val id: String? = null,
    val sequence: Int? = null,
    val linear: VastLinear? = null,
)

data class VastLinear(
    val durationMs: Long? = null,
    val mediaFiles: List<VastMediaFile> = emptyList(),
    val trackingEvents: Map<String, List<String>> = emptyMap(),
    val clickThrough: String? = null,
)

data class VastMediaFile(
    val uri: String,
    val mimeType: String? = null,
    val bitrateKbps: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
)

