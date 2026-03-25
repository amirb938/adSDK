package tech.done.adsdk.parser.impl

import tech.done.adsdk.parser.VmapParser
import tech.done.adsdk.parser.error.VmapParseError
import tech.done.adsdk.parser.error.XmlParseError
import tech.done.adsdk.parser.internal.parseVmapTimeOffsetToMs
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
        p.nextTag()
        if (p.tagName() != "VMAP") {
            throw VmapParseError(XmlParseError.Code.MalformedXml, "Root tag must be <VMAP> but was <${p.name}>", line = p.lineNumber, column = p.columnNumber)
        }

        val version = p.getAttributeValue(null, "version")
        val breaks = mutableListOf<AdBreak>()

        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_TAG && p.tagName() == "VMAP") break
            if (event != XmlPullParser.START_TAG) continue

            when (p.tagName()) {
                "AdBreak" -> breaks += readAdBreak(p) ?: run { /* ignore empty */; continue }
                else -> p.skipTag()
            }
        }

        return VmapResponse(version = version, adBreaks = breaks)
    }

    private fun readAdBreak(p: XmlPullParser): AdBreak? {
        val breakId = p.getAttributeValue(null, "breakId")
        val timeOffsetRaw = p.getAttributeValue(null, "timeOffset")?.trim().orEmpty()

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
                position = Position.Midroll
                timeOffsetMs = parseVmapTimeOffsetToMs(timeOffsetRaw)
            }
        }

        var vastAdTagUri: String? = null

        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_TAG && p.tagName() == "AdBreak") break
            if (event != XmlPullParser.START_TAG) continue

            when (p.tagName()) {
                "AdSource" -> vastAdTagUri = readAdSource(p)
                else -> p.skipTag()
            }
        }

        if (vastAdTagUri.isNullOrBlank()) return null
        if (position == Position.Midroll && timeOffsetMs == null) return null

        return AdBreak(
            breakId = breakId,
            position = position,
            timeOffsetMs = timeOffsetMs,
            vastAdTagUri = vastAdTagUri,
        )
    }

    private fun readAdSource(p: XmlPullParser): String? {
        var vastAdTagUri: String? = null
        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_TAG && p.tagName() == "AdSource") break
            if (event != XmlPullParser.START_TAG) continue

            when (p.tagName()) {
                "AdTagURI" -> {
                    vastAdTagUri = p.readText()
                }
                else -> p.skipTag()
            }
        }
        return vastAdTagUri
    }
}

