package tech.done.ads.parser.model

data class VMAPResponse(
    val version: String? = null,
    val adBreaks: List<AdBreak> = emptyList(),
)

data class AdBreak(
    val breakId: String? = null,
    val position: Position,
    val timeOffsetMs: Long? = null,
    val vastAdTagUri: String? = null,
    val vastInlineXml: String? = null,
)

enum class Position {
    Preroll,
    Midroll,
    Postroll,
}

