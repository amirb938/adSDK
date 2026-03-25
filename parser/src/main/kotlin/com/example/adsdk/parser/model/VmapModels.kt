package com.example.adsdk.parser.model

data class VmapDocument(
    val version: String? = null,
    val adBreaks: List<VmapAdBreak> = emptyList(),
)

data class VmapAdBreak(
    val timeOffset: String,
    val breakType: String? = null,
    val breakId: String? = null,
    val repeatAfter: String? = null,
    val adSource: VmapAdSource? = null,
    val trackingEvents: Map<String, List<String>> = emptyMap(),
)

data class VmapAdSource(
    val id: String? = null,
    val allowMultipleAds: Boolean? = null,
    val followRedirects: Boolean? = null,
    val vastAdTagUri: String? = null,
    val vastData: String? = null,
)

