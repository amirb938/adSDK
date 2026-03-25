package tech.done.adsdk.parser

import tech.done.adsdk.parser.model.VmapResponse

/**
 * VMAP parsing contract.
 *
 * Pure Kotlin: no Android framework dependencies.
 */
interface VmapParser {
    fun parse(xml: String): VmapResponse
}

