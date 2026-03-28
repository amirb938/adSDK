package tech.done.ads.parser.impl

import tech.done.ads.parser.VMAPParser
import tech.done.ads.parser.error.VMAPParseError
import tech.done.ads.parser.error.XmlParseError
import tech.done.ads.parser.internal.AdSdkDebugLog
import tech.done.ads.parser.internal.parseVMAPTimeOffsetToMs
import tech.done.ads.parser.internal.readElementXml
import tech.done.ads.parser.internal.readText
import tech.done.ads.parser.internal.skipTag
import tech.done.ads.parser.internal.tagName
import tech.done.ads.parser.model.AdBreak
import tech.done.ads.parser.model.Position
import tech.done.ads.parser.model.VMAPResponse
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

class VMAPPullParser : VMAPParser {
    override fun parse(xml: String): VMAPResponse {
        val logTag = "Parser/VMAP"
        AdSdkDebugLog.d(logTag, "parse xmlLength=${xml.length}")
        val parser = try {
            PullParserFactory.newParser(xml)
        } catch (t: Throwable) {
            throw VMAPParseError(XmlParseError.Code.MalformedXml, "Failed to init VMAP parser", cause = t)
        }

        return try {
            parseDocument(parser)
        } catch (e: VMAPParseError) {
            throw e
        } catch (e: XmlPullParserException) {
            throw VMAPParseError(XmlParseError.Code.MalformedXml, e.message ?: "Malformed VMAP", line = e.lineNumber, column = e.columnNumber, cause = e)
        } catch (t: Throwable) {
            throw VMAPParseError(XmlParseError.Code.MalformedXml, t.message ?: "Malformed VMAP", cause = t)
        }
    }

    private fun parseDocument(p: XmlPullParser): VMAPResponse {
        val logTag = "Parser/VMAP"
        p.nextTag()
        if (p.tagName() != "VMAP") {
            throw VMAPParseError(XmlParseError.Code.MalformedXml, "Root tag must be <VMAP> but was <${p.name}>", line = p.lineNumber, column = p.columnNumber)
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
        return VMAPResponse(version = version, adBreaks = breaks)
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
                val parsedMs = parseVMAPTimeOffsetToMs(timeOffsetRaw)
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
                    AdSdkDebugLog.d(logTag, "Found <${p.name}> (inline VAST). Extracting inner <VAST> XML.")
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

