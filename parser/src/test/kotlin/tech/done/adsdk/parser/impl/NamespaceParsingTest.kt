package tech.done.adsdk.parser.impl

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.done.adsdk.parser.error.VmapParseError
import java.io.File

class NamespaceParsingTest {

    @Test
    fun `VMAP parses with namespace prefix and without`() {
        val xmlPrefixed = readProjectFile("sample-app/src/main/assets/sample_vmap.xml")
        val parsedPrefixed = VmapPullParser().parse(xmlPrefixed)
        assertTrue(parsedPrefixed.adBreaks.isNotEmpty())

        val xmlNoPrefix = xmlPrefixed
            .replace("xmlns:vmap=\"http://www.iab.net/videosuite/vmap\"", "")
            .replace("vmap:", "")
        val parsedNoPrefix = VmapPullParser().parse(xmlNoPrefix)
        assertTrue(parsedNoPrefix.adBreaks.isNotEmpty())
    }

    @Test
    fun `VMAP root tag error shows qualified name but does not crash`() {
        val bad = "<vmap:NotVMAP xmlns:vmap=\"http://www.iab.net/videosuite/vmap\"/>"
        try {
            VmapPullParser().parse(bad)
            throw AssertionError("Expected VmapParseError")
        } catch (e: VmapParseError) {
            // ok
        }
    }

    @Test
    fun `VAST parses with prefixed tags and without`() = runBlocking {
        val xml = readProjectFile("sample-app/src/main/assets/sample_vast.xml")

        val parsedPlain = VastPullParser().parse(xml = xml, wrapperFetcher = null)
        assertTrue(parsedPlain.isNotEmpty())

        val prefixed = prefixVast(xml)
        val parsedPrefixed = VastPullParser().parse(xml = prefixed, wrapperFetcher = null)
        assertTrue(parsedPrefixed.isNotEmpty())
    }

    private fun readProjectFile(relativePath: String): String {
        val root = projectRoot()
        return File(root, relativePath).readText()
    }

    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: error("Could not locate project root from user.dir")
        }
    }

    private fun prefixVast(xml: String): String {
        // Make sure only root carries xmlns.
        var out = xml
        val rootOpenIdx = out.indexOf("<VAST")
        if (rootOpenIdx >= 0) {
            out = out.replaceFirst("<VAST", "<vast:VAST xmlns:vast=\"urn:vast\"")
            out = out.replaceFirst("</VAST>", "</vast:VAST>")
        } else if (out.contains("<vast:VAST")) {
            // already prefixed
            return out
        } else {
            error("No VAST root tag found")
        }

        val tags = listOf(
            "Ad",
            "AdTitle",
            "AdSystem",
            "InLine",
            "Wrapper",
            "VASTAdTagURI",
            "Impression",
            "Creatives",
            "Creative",
            "Linear",
            "Duration",
            "TrackingEvents",
            "Tracking",
            "MediaFiles",
            "MediaFile",
        )
        for (t in tags) {
            // Prefix only when we're certain it's the tag name, not a longer tag starting with same letters (e.g. Ad vs AdTitle).
            out = out.replace(Regex("<$t(?=[\\s>/])"), "<vast:$t")
            out = out.replace("</$t>", "</vast:$t>")
        }
        return out
    }
}

