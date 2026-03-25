package com.example.adsdk.parser

import com.example.adsdk.parser.model.VmapResponse

/**
 * VMAP parsing contract.
 *
 * Pure Kotlin: no Android framework dependencies.
 */
interface VmapParser {
    fun parse(xml: String): VmapResponse
}

