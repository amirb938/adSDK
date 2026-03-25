package tech.done.adsdk.parser.internal

import org.kxml2.io.KXmlSerializer
import org.xmlpull.v1.XmlPullParser
import java.io.StringWriter

internal fun XmlPullParser.tagName(): String = (name ?: "").substringAfter(':')

internal fun XmlPullParser.requireStartTag(name: String) {
    require(eventType == XmlPullParser.START_TAG) { "Expected START_TAG <$name> but was event=$eventType name=$this.name" }
    require(this.tagName() == name) { "Expected <$name> but was <${this.name}>" }
}

internal fun XmlPullParser.readText(): String {
    var result = ""
    if (next() == XmlPullParser.TEXT) {
        result = text ?: ""
        nextTag()
    }
    return result.trim()
}

internal fun XmlPullParser.skipTag() {
    if (eventType != XmlPullParser.START_TAG) return
    var depth = 1
    while (depth != 0) {
        when (next()) {
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
        }
    }
}

/**
 * Serializes the current START_TAG element (including all children) to XML.
 *
 * - Streaming (no DOM)
 * - Namespace-safe (uses the qualified [name] coming from the parser)
 */
internal fun XmlPullParser.readElementXml(): String {
    require(eventType == XmlPullParser.START_TAG) { "Expected START_TAG but was event=$eventType name=$name" }

    val writer = StringWriter()
    val serializer = KXmlSerializer().apply {
        setOutput(writer)
    }

    fun writeStartTag() {
        val tag = name ?: ""
        serializer.startTag(null, tag)
        for (i in 0 until attributeCount) {
            // XmlPullParser API surface differs by implementation; these are the portable accessors.
            serializer.attribute(getAttributeNamespace(i), getAttributeName(i), getAttributeValue(i))
        }
    }

    fun writeEndTag() {
        val tag = name ?: ""
        serializer.endTag(null, tag)
    }

    // We are on START_TAG of the root element to serialize.
    writeStartTag()
    var depth = 1

    while (depth > 0) {
        when (next()) {
            XmlPullParser.START_TAG -> {
                writeStartTag()
                depth++
            }
            XmlPullParser.END_TAG -> {
                writeEndTag()
                depth--
            }
            XmlPullParser.TEXT -> {
                serializer.text(text ?: "")
            }
            else -> {
                // ignore
            }
        }
    }

    serializer.flush()
    return writer.toString()
}

internal fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

internal fun String.toIntOrNullSafe(): Int? = runCatching { trim().toInt() }.getOrNull()

