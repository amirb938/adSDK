package tech.done.ads.parser.model

data class VASTAd(
    val adId: String? = null,
    val sequence: Int? = null,
    val isWrapper: Boolean = false,
    val skipOffset: SkipOffset? = null,
    val durationMs: Long? = null,
    val mediaFiles: List<MediaFile> = emptyList(),
    val trackingEvents: Map<String, List<String>> = emptyMap(),
)

sealed class SkipOffset {
    data class TimeMs(val value: Long) : SkipOffset()
    data class Percent(val value: Int) : SkipOffset()
}

data class MediaFile(
    val uri: String,
    val mimeType: String? = null,
    val bitrateKbps: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
)

