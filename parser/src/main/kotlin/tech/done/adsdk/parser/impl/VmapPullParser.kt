package tech.done.adsdk.parser.impl

import tech.done.adsdk.parser.VmapParser
import tech.done.adsdk.parser.error.VmapParseError
import tech.done.adsdk.parser.error.XmlParseError
import tech.done.adsdk.parser.internal.AdSdkDebugLog
import tech.done.adsdk.parser.internal.parseVmapTimeOffsetToMs
import tech.done.adsdk.parser.internal.readElementXml
import tech.done.adsdk.parser.internal.readText
import tech.done.adsdk.parser.internal.skipTag
import tech.done.adsdk.parser.internal.tagName
import tech.done.adsdk.parser.model.AdBreak
import tech.done.adsdk.parser.model.Position
import tech.done.adsdk.parser.model.VmapResponse
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

class VmapPullParser : VmapParser {
    override fun parse(xml: String): VmapResponse {
        val logTag = "Parser/VMAP"
        AdSdkDebugLog.d(logTag, "parse xmlLength=${xml.length}")
        val parser = try {
            PullParserFactory.newParser(xml)
        } catch (t: Throwable) {
            throw VmapParseError(XmlParseError.Code.MalformedXml, "Failed to init VMAP parser", cause = t)
        }

        return try {
            parseDocument(parser)
        } catch (e: VmapParseError) {
            throw e
        } catch (e: XmlPullParserException) {
            throw VmapParseError(XmlParseError.Code.MalformedXml, e.message ?: "Malformed VMAP", line = e.lineNumber, column = e.columnNumber, cause = e)
        } catch (t: Throwable) {
            throw VmapParseError(XmlParseError.Code.MalformedXml, t.message ?: "Malformed VMAP", cause = t)
        }
    }

    private fun parseDocument(p: XmlPullParser): VmapResponse {
        val logTag = "Parser/VMAP"
        p.nextTag()
        if (p.tagName() != "VMAP") {
            throw VmapParseError(XmlParseError.Code.MalformedXml, "Root tag must be <VMAP> but was <${p.name}>", line = p.lineNumber, column = p.columnNumber)
        }

        val version = p.getAttributeValue(null, "version")
        AdSdkDebugLog.d(logTag, "root <${p.name}> local=${p.tagName()} version=$version")
        val breaks = mutableListOf<AdBreak>()

        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_TAG && p.tagName() == "VMAP") break
            if (event != XmlPullParser.START_TAG) continue

            when (p.tagName()) {
                "AdBreak" -> {
                    val b = readAdBreak(p)
                    if (b == null) {
                        AdSdkDebugLog.d(logTag, "ignored <${p.name}> (empty/invalid break)")
                        continue
                    }
                    breaks += b
                }
                else -> p.skipTag()
            }
        }

        AdSdkDebugLog.d(logTag, "parsed breaks=${breaks.size}")
        return VmapResponse(version = version, adBreaks = breaks)
    }

    private fun readAdBreak(p: XmlPullParser): AdBreak? {
        val logTag = "Parser/VMAP"
        val breakId = p.getAttributeValue(null, "breakId")
        val timeOffsetRaw = p.getAttributeValue(null, "timeOffset")?.trim().orEmpty()
        AdSdkDebugLog.d(logTag, "<${p.name}> breakId=$breakId timeOffsetRaw=$timeOffsetRaw")

        val position: Position
        val timeOffsetMs: Long?
        when (timeOffsetRaw.lowercase()) {
            "start" -> {
                position = Position.Preroll
                timeOffsetMs = 0L
            }
            "end" -> {
                position = Position.Postroll
                timeOffsetMs = null
            }
            else -> {
                val parsedMs = parseVmapTimeOffsetToMs(timeOffsetRaw)
                // Treat 00:00:00 (and any numeric offset that resolves to 0) as preroll.
                // This is common in VMAPs that use explicit time format instead of "start".
                if (parsedMs == 0L) {
                    position = Position.Preroll
                    timeOffsetMs = 0L
                } else {
                    position = Position.Midroll
                    timeOffsetMs = parsedMs
                }
            }
        }
        AdSdkDebugLog.d(logTag, "breakId=$breakId position=$position timeOffsetMs=$timeOffsetMs")

        var vastAdTagUri: String? = null
        var vastInlineXml: String? = null

        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_TAG && p.tagName() == "AdBreak") break
            if (event != XmlPullParser.START_TAG) continue

            when (p.tagName()) {
                "AdSource" -> {
                    val res = readAdSource(p)
                    vastAdTagUri = res.vastAdTagUri
                    vastInlineXml = res.vastInlineXml
                }
                else -> p.skipTag()
            }
        }

        AdSdkDebugLog.d(
            logTag,
            "breakId=$breakId resolved vastAdTagUri=$vastAdTagUri inlineBytes=${vastInlineXml?.length}",
        )
        if (vastAdTagUri.isNullOrBlank() && vastInlineXml.isNullOrBlank()) return null
        if (position == Position.Midroll && timeOffsetMs == null) return null

        return AdBreak(
            breakId = breakId,
            position = position,
            timeOffsetMs = timeOffsetMs,
            vastAdTagUri = vastAdTagUri,
            vastInlineXml = vastInlineXml,
        )
    }

    private data class AdSourceResult(
        val vastAdTagUri: String?,
        val vastInlineXml: String?,
    )

    private fun readAdSource(p: XmlPullParser): AdSourceResult {
        val logTag = "Parser/VMAP"
        AdSdkDebugLog.d(logTag, "<${p.name}> reading AdSource (note: this parser currently expects <AdTagURI>)")
        var vastAdTagUri: String? = null
        var vastInlineXml: String? = null
        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_TAG && p.tagName() == "AdSource") break
            if (event != XmlPullParser.START_TAG) continue

            when (p.tagName()) {
                "AdTagURI" -> {
                    vastAdTagUri = p.readText()
                    AdSdkDebugLog.d(logTag, "AdTagURI=$vastAdTagUri")
                }
                "VASTAdData" -> {
                    // Inline VAST under VMAP. We extract the <VAST> element as XML.
                    AdSdkDebugLog.d(logTag, "Found <${p.name}> (inline VAST). Extracting inner <VAST> XML.")
                    // Move to the first child element inside VASTAdData (typically <VAST>).
                    var extracted: String? = null
                    while (true) {
                        val ev = p.next()
                        if (ev == XmlPullParser.END_TAG && p.tagName() == "VASTAdData") break
                        if (ev != XmlPullParser.START_TAG) continue
                        extracted = p.readElementXml()
                        break
                    }
                    vastInlineXml = extracted
                    AdSdkDebugLog.d(logTag, "inline VAST extracted bytes=${vastInlineXml?.length}")
                }
                else -> p.skipTag()
            }
        }
        return AdSourceResult(vastAdTagUri = vastAdTagUri, vastInlineXml = vastInlineXml)
    }
}

