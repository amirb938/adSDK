package tech.done.ads.parser.internal

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
            serializer.attribute(getAttributeNamespace(i), getAttributeName(i), getAttributeValue(i))
        }
    }

    fun writeEndTag() {
        val tag = name ?: ""
        serializer.endTag(null, tag)
    }

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
            }
        }
    }

    serializer.flush()
    return writer.toString()
}

internal fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

internal fun String.toIntOrNullSafe(): Int? = runCatching { trim().toInt() }.getOrNull()

