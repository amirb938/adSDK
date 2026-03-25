package com.example.adsdk.parser.internal

import org.xmlpull.v1.XmlPullParser

internal fun XmlPullParser.requireStartTag(name: String) {
    require(eventType == XmlPullParser.START_TAG) { "Expected START_TAG <$name> but was event=$eventType name=$this.name" }
    require(this.name == name) { "Expected <$name> but was <$this.name>" }
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

internal fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

internal fun String.toIntOrNullSafe(): Int? = runCatching { trim().toInt() }.getOrNull()

