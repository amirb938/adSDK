package tech.done.adsdk.parser.impl

import tech.done.adsdk.parser.VastParser
import tech.done.adsdk.parser.VastWrapperFetcher
import tech.done.adsdk.parser.error.VastParseError
import tech.done.adsdk.parser.error.XmlParseError
import tech.done.adsdk.parser.internal.attr
import tech.done.adsdk.parser.internal.parseVastTimeToMs
import tech.done.adsdk.parser.internal.readText
import tech.done.adsdk.parser.internal.skipTag
import tech.done.adsdk.parser.internal.toIntOrNullSafe
import tech.done.adsdk.parser.model.MediaFile
import tech.done.adsdk.parser.model.SkipOffset
import tech.done.adsdk.parser.model.VastAd
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

class VastPullParser(
    private val maxWrapperDepth: Int = 5,
) : VastParser {

    override suspend fun parse(xml: String, wrapperFetcher: VastWrapperFetcher?): List<VastAd> {
        return parseInternal(xml = xml, wrapperFetcher = wrapperFetcher, depth = 0, visited = LinkedHashSet())
    }

    private suspend fun parseInternal(
        xml: String,
        wrapperFetcher: VastWrapperFetcher?,
        depth: Int,
        visited: LinkedHashSet<String>,
    ): List<VastAd> {
        val p = try {
            PullParserFactory.newParser(xml)
        } catch (t: Throwable) {
            throw VastParseError(XmlParseError.Code.MalformedXml, "Failed to init VAST parser", cause = t)
        }

        val parsed = try {
            parseDocument(p)
        } catch (e: VastParseError) {
            throw e
        } catch (e: XmlPullParserException) {
            throw VastParseError(XmlParseError.Code.MalformedXml, e.message ?: "Malformed VAST", line = e.lineNumber, column = e.columnNumber, cause = e)
        } catch (t: Throwable) {
            throw VastParseError(XmlParseError.Code.MalformedXml, t.message ?: "Malformed VAST", cause = t)
        }

        if (wrapperFetcher == null) return parsed

        val out = mutableListOf<VastAd>()
        for (ad in parsed) {
            if (!ad.isWrapper) {
                if (ad.mediaFiles.isNotEmpty()) out += ad
                continue
            }

            // We encode wrapper target URL in a trackingEvents entry key for now? No: wrapper-only VastAd has no media.
            // Instead, we stored wrapper target as a synthetic event key in parseDocumentWrapperTarget.
            val wrapperUrl = ad.trackingEvents[WRAPPER_URL_KEY]?.firstOrNull()
            if (wrapperUrl.isNullOrBlank()) continue

            if (depth >= maxWrapperDepth) {
                throw VastParseError(XmlParseError.Code.WrapperDepthExceeded, "Max wrapper depth exceeded ($maxWrapperDepth)")
            }
            if (!visited.add(wrapperUrl)) {
                throw VastParseError(XmlParseError.Code.WrapperLoopDetected, "Wrapper loop detected at $wrapperUrl")
            }

            val nextXml = wrapperFetcher.fetchXml(wrapperUrl)
            val resolved = parseInternal(nextXml, wrapperFetcher, depth + 1, visited)
            visited.remove(wrapperUrl)

            // Merge wrapper tracking into resolved ads.
            val wrapperTracking = ad.trackingEvents.filterKeys { it != WRAPPER_URL_KEY }
            for (resolvedAd in resolved) {
                val merged = resolvedAd.copy(
                    trackingEvents = mergeTracking(wrapperTracking, resolvedAd.trackingEvents),
                )
                if (merged.mediaFiles.isNotEmpty()) out += merged
            }
        }
        return out
    }

    private fun parseDocument(p: XmlPullParser): List<VastAd> {
        p.nextTag()
        if (p.name != "VAST") {
            throw VastParseError(XmlParseError.Code.MalformedXml, "Root tag must be <VAST> but was <${p.name}>", line = p.lineNumber, column = p.columnNumber)
        }

        val version = p.attr("version")?.trim()
        if (version != null && !version.startsWith("3") && !version.startsWith("4")) {
            throw VastParseError(XmlParseError.Code.UnsupportedVersion, "Unsupported VAST version: $version", line = p.lineNumber, column = p.columnNumber)
        }

        val ads = mutableListOf<VastAd>()
        while (p.next() != XmlPullParser.END_TAG || p.name != "VAST") {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "Ad" -> {
                    readAd(p)?.let { if (it.mediaFiles.isNotEmpty() || it.isWrapper) ads += it }
                }
                else -> p.skipTag()
            }
        }
        return ads
    }

    private fun readAd(p: XmlPullParser): VastAd? {
        val adId = p.attr("id")
        val sequence = p.attr("sequence")?.toIntOrNullSafe()

        var wrapperUrl: String? = null
        var isWrapper = false

        var durationMs: Long? = null
        var skipOffset: SkipOffset? = null
        val mediaFiles = mutableListOf<MediaFile>()
        val tracking = mutableMapOf<String, MutableList<String>>()

        while (p.next() != XmlPullParser.END_TAG || p.name != "Ad") {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "InLine" -> {
                    isWrapper = false
                    readInline(p, tracking, mediaFiles).also {
                        durationMs = it.durationMs
                        skipOffset = it.skipOffset
                    }
                }
                "Wrapper" -> {
                    isWrapper = true
                    wrapperUrl = readWrapper(p, tracking)
                }
                else -> p.skipTag()
            }
        }

        // Empty ad -> ignore.
        if (!isWrapper && mediaFiles.isEmpty()) return null

        val normalizedTracking = tracking.mapValues { it.value.distinct() }
        val trackingWithWrapper = if (wrapperUrl != null) {
            normalizedTracking.toMutableMap().apply {
                put(WRAPPER_URL_KEY, listOf(wrapperUrl))
            }
        } else normalizedTracking

        return VastAd(
            adId = adId,
            sequence = sequence,
            isWrapper = isWrapper,
            skipOffset = skipOffset,
            durationMs = durationMs,
            mediaFiles = mediaFiles,
            trackingEvents = trackingWithWrapper,
        )
    }

    private data class InlineResult(
        val durationMs: Long?,
        val skipOffset: SkipOffset?,
    )

    private fun readInline(
        p: XmlPullParser,
        tracking: MutableMap<String, MutableList<String>>,
        mediaFiles: MutableList<MediaFile>,
    ): InlineResult {
        var durationMs: Long? = null
        var skipOffset: SkipOffset? = null

        while (p.next() != XmlPullParser.END_TAG || p.name != "InLine") {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "Impression" -> tracking.getOrPut("impression") { mutableListOf() }.add(p.readText())
                "Creatives" -> {
                    val res = readCreatives(p, tracking, mediaFiles)
                    if (durationMs == null) durationMs = res.durationMs
                    if (skipOffset == null) skipOffset = res.skipOffset
                }
                else -> p.skipTag()
            }
        }

        return InlineResult(durationMs = durationMs, skipOffset = skipOffset)
    }

    private data class CreativeResult(
        val durationMs: Long?,
        val skipOffset: SkipOffset?,
    )

    private fun readCreatives(
        p: XmlPullParser,
        tracking: MutableMap<String, MutableList<String>>,
        mediaFiles: MutableList<MediaFile>,
    ): CreativeResult {
        var durationMs: Long? = null
        var skipOffset: SkipOffset? = null

        while (p.next() != XmlPullParser.END_TAG || p.name != "Creatives") {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "Creative" -> {
                    val res = readCreative(p, tracking, mediaFiles)
                    if (durationMs == null) durationMs = res.durationMs
                    if (skipOffset == null) skipOffset = res.skipOffset
                }
                else -> p.skipTag()
            }
        }

        return CreativeResult(durationMs = durationMs, skipOffset = skipOffset)
    }

    private fun readCreative(
        p: XmlPullParser,
        tracking: MutableMap<String, MutableList<String>>,
        mediaFiles: MutableList<MediaFile>,
    ): CreativeResult {
        var durationMs: Long? = null
        var skipOffset: SkipOffset? = null

        while (p.next() != XmlPullParser.END_TAG || p.name != "Creative") {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "Linear" -> {
                    val res = readLinear(p, tracking, mediaFiles)
                    durationMs = res.durationMs
                    skipOffset = res.skipOffset
                }
                else -> p.skipTag()
            }
        }

        return CreativeResult(durationMs = durationMs, skipOffset = skipOffset)
    }

    private data class LinearResult(
        val durationMs: Long?,
        val skipOffset: SkipOffset?,
    )

    private fun readLinear(
        p: XmlPullParser,
        tracking: MutableMap<String, MutableList<String>>,
        mediaFiles: MutableList<MediaFile>,
    ): LinearResult {
        val skipOffsetRaw = p.attr("skipoffset")?.trim()
        val skipOffset = parseSkipOffset(skipOffsetRaw)
        var durationMs: Long? = null

        while (p.next() != XmlPullParser.END_TAG || p.name != "Linear") {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "Duration" -> durationMs = parseVastTimeToMs(p.readText())
                "TrackingEvents" -> readTrackingEvents(p, tracking)
                "MediaFiles" -> readMediaFiles(p, mediaFiles)
                else -> p.skipTag()
            }
        }

        return LinearResult(durationMs = durationMs, skipOffset = skipOffset)
    }

    private fun readTrackingEvents(p: XmlPullParser, tracking: MutableMap<String, MutableList<String>>) {
        while (p.next() != XmlPullParser.END_TAG || p.name != "TrackingEvents") {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "Tracking" -> {
                    val event = p.attr("event")?.trim()?.lowercase()
                    val url = p.readText()
                    if (!event.isNullOrBlank() && url.isNotBlank()) {
                        tracking.getOrPut(event) { mutableListOf() }.add(url)
                    }
                }
                else -> p.skipTag()
            }
        }
    }

    private fun readMediaFiles(p: XmlPullParser, mediaFiles: MutableList<MediaFile>) {
        while (p.next() != XmlPullParser.END_TAG || p.name != "MediaFiles") {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "MediaFile" -> {
                    val mime = p.attr("type")
                    val bitrate = p.attr("bitrate")?.toIntOrNullSafe()
                    val width = p.attr("width")?.toIntOrNullSafe()
                    val height = p.attr("height")?.toIntOrNullSafe()
                    val uri = p.readText()
                    if (uri.isNotBlank()) {
                        mediaFiles += MediaFile(uri = uri, mimeType = mime, bitrateKbps = bitrate, width = width, height = height)
                    }
                }
                else -> p.skipTag()
            }
        }
    }

    private fun readWrapper(p: XmlPullParser, tracking: MutableMap<String, MutableList<String>>): String? {
        var url: String? = null
        while (p.next() != XmlPullParser.END_TAG || p.name != "Wrapper") {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "VASTAdTagURI" -> url = p.readText()
                "Impression" -> tracking.getOrPut("impression") { mutableListOf() }.add(p.readText())
                "Creatives" -> {
                    // Wrapper tracking events can exist inside Creatives/Creative/Linear/TrackingEvents
                    readCreatives(p, tracking, mediaFiles = mutableListOf())
                }
                else -> p.skipTag()
            }
        }
        return url
    }

    private fun parseSkipOffset(raw: String?): SkipOffset? {
        if (raw.isNullOrBlank()) return null
        val v = raw.trim()
        return if (v.endsWith("%")) {
            val pct = v.removeSuffix("%").toIntOrNull()
            if (pct != null) SkipOffset.Percent(pct) else null
        } else {
            val ms = parseVastTimeToMs(v)
            if (ms != null) SkipOffset.TimeMs(ms) else null
        }
    }

    private fun mergeTracking(
        wrapper: Map<String, List<String>>,
        inline: Map<String, List<String>>,
    ): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        for ((k, v) in wrapper) out.getOrPut(k) { mutableListOf() }.addAll(v)
        for ((k, v) in inline) out.getOrPut(k) { mutableListOf() }.addAll(v)
        return out.mapValues { it.value.distinct() }
    }

    private companion object {
        // Internal-only way to carry wrapper target without adding new model fields.
        private const val WRAPPER_URL_KEY = "__wrapper_url"
    }
}
