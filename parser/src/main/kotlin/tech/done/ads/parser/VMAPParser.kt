package tech.done.ads.parser

import tech.done.ads.parser.model.VMAPResponse


interface VMAPParser {
    fun parse(xml: String): VMAPResponse
}

