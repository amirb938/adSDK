package com.example.adsdk.parser

import com.example.adsdk.parser.model.VastDocument

/**
 * VAST parsing contract.
 *
 * Pure Kotlin: no Android framework dependencies.
 */
interface VastParser {
    fun parse(xml: String): VastDocument
}

