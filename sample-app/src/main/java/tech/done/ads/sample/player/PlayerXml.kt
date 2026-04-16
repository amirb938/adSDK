package tech.done.ads.sample.player

import android.content.Context

fun Context.readAssetText(name: String): String =
    assets.open(name).bufferedReader().use { it.readText() }

fun wrapVastAsVmapPreroll(vastXml: String): String {
    val vast = vastXml
        .trimStart()
        .removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        .removePrefix("<?xml version='1.0' encoding='UTF-8'?>")
        .trimStart()
    return """
        <vmap:VMAP xmlns:vmap="http://www.iab.net/videosuite/vmap" version="1.0">
          <vmap:AdBreak breakType="linear" timeOffset="00:00:00" breakId="preroll-1">
            <vmap:AdSource allowMultipleAds="false" followRedirects="true">
              <vmap:VASTAdData>
                $vast
              </vmap:VASTAdData>
            </vmap:AdSource>
          </vmap:AdBreak>
        </vmap:VMAP>
    """.trimIndent()
}

