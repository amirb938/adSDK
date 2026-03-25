package com.example.adsdk.parser

import com.example.adsdk.parser.model.VmapDocument

/**
 * VMAP parsing contract.
 *
 * Pure Kotlin: no Android framework dependencies.
 */
interface VmapParser {
    fun parse(xml: String): VmapDocument
}

