package com.example.adsdk.parser.internal

internal fun parseVastTimeToMs(value: String): Long? {
    val v = value.trim()
    // Supports HH:MM:SS and HH:MM:SS.mmm
    val parts = v.split(":")
    if (parts.size != 3) return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val secPart = parts[2]
    val secAndMs = secPart.split(".")
    val seconds = secAndMs[0].toLongOrNull() ?: return null
    val ms = if (secAndMs.size > 1) secAndMs[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L else 0L
    return (((hours * 60 + minutes) * 60 + seconds) * 1000L) + ms
}

internal fun parseVmapTimeOffsetToMs(value: String): Long? {
    return parseVastTimeToMs(value)
}

