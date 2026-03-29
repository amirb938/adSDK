package tech.done.ads.parser.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.done.ads.parser.VASTWrapperFetcher
import tech.done.ads.parser.error.VASTParseError
import tech.done.ads.parser.error.VMAPParseError
import tech.done.ads.parser.model.Position
import tech.done.ads.parser.model.SkipOffset

class VASTVMAPPullParserScenariosTest {

    private val vmapParser = VMAPPullParser()
    private val vastParser = VASTPullParser()

    @Test
    fun `VMAP preroll timeOffset start`() {
        val xml = """
            <VMAP version="1.0">
              <AdBreak timeOffset="start" breakId="p1">
                <AdSource><AdTagURI>https://example.com/vast</AdTagURI></AdSource>
              </AdBreak>
            </VMAP>
        """.trimIndent()
        val r = vmapParser.parse(xml)
        assertEquals(1, r.adBreaks.size)
        assertEquals(Position.Preroll, r.adBreaks[0].position)
        assertEquals(0L, r.adBreaks[0].timeOffsetMs)
        assertEquals("https://example.com/vast", r.adBreaks[0].vastAdTagUri)
    }

    @Test
    fun `VMAP preroll timeOffset 00-00-00`() {
        val xml = """
            <VMAP version="1.0">
              <AdBreak timeOffset="00:00:00" breakId="p0">
                <AdSource><AdTagURI>https://a/v</AdTagURI></AdSource>
              </AdBreak>
            </VMAP>
        """.trimIndent()
        val b = vmapParser.parse(xml).adBreaks.single()
        assertEquals(Position.Preroll, b.position)
        assertEquals(0L, b.timeOffsetMs)
    }

    @Test
    fun `VMAP midroll timeOffset HH-MM-SS`() {
        val xml = """
            <VMAP version="1.0">
              <AdBreak timeOffset="00:01:30.000" breakId="m1">
                <AdSource><AdTagURI>https://mid/v</AdTagURI></AdSource>
              </AdBreak>
            </VMAP>
        """.trimIndent()
        val b = vmapParser.parse(xml).adBreaks.single()
        assertEquals(Position.Midroll, b.position)
        assertEquals(90_000L, b.timeOffsetMs)
    }

    @Test
    fun `VMAP postroll end`() {
        val xml = """
            <VMAP version="1.0">
              <AdBreak timeOffset="end" breakId="po">
                <AdSource><AdTagURI>https://post/v</AdTagURI></AdSource>
              </AdBreak>
            </VMAP>
        """.trimIndent()
        val b = vmapParser.parse(xml).adBreaks.single()
        assertEquals(Position.Postroll, b.position)
        assertEquals(null, b.timeOffsetMs)
    }

    @Test
    fun `VMAP VASTAdData inline extracts inner VAST`() {
        val inner = """
            <VAST version="3.0"><Ad><InLine>
              <Impression>https://imp</Impression>
              <Creatives><Creative><Linear skipoffset="00:00:05">
                <Duration>00:00:30</Duration>
                <MediaFiles><MediaFile type="video/mp4">https://media/mp4</MediaFile></MediaFiles>
                <TrackingEvents></TrackingEvents>
              </Linear></Creative></Creatives>
            </InLine></Ad></VAST>
        """.trimIndent().replace("\n", "")
        val xml = """
            <VMAP version="1.0">
              <AdBreak timeOffset="start" breakId="in">
                <AdSource><VASTAdData>$inner</VASTAdData></AdSource>
              </AdBreak>
            </VMAP>
        """.trimIndent()
        val b = vmapParser.parse(xml).adBreaks.single()
        assertNotNull(b.vastInlineXml)
        assertTrue(b.vastInlineXml!!.contains("<VAST"))
        assertTrue(b.vastAdTagUri.isNullOrBlank())
    }

    @Test
    fun `VMAP invalid root throws`() {
        assertThrows<VMAPParseError> {
            vmapParser.parse("<VAST version=\"3.0\"></VAST>")
        }
    }

    @Test
    fun `VMAP AdBreak without VAST source dropped`() {
        val xml = """
            <VMAP version="1.0">
              <AdBreak timeOffset="start" breakId="empty">
                <AdSource></AdSource>
              </AdBreak>
            </VMAP>
        """.trimIndent()
        assertTrue(vmapParser.parse(xml).adBreaks.isEmpty())
    }

    @Test
    fun `VAST inline linear parses media duration tracking`() = runBlocking {
        val xml = """
            <VAST version="3.0">
              <Ad id="a1" sequence="1">
                <InLine>
                  <Impression>https://i1</Impression>
                  <Creatives>
                    <Creative>
                      <Linear skipoffset="10%">
                        <Duration>00:00:20</Duration>
                        <InteractiveCreativeFile apiFramework="SIMID"><![CDATA[https://cdn/simid/index.html]]></InteractiveCreativeFile>
                        <TrackingEvents>
                          <Tracking event="start"><![CDATA[https://t/start]]></Tracking>
                          <Tracking event="firstQuartile"><![CDATA[https://t/q1]]></Tracking>
                          <Tracking event="progress" offset="00:00:05"><![CDATA[https://t/p5]]></Tracking>
                        </TrackingEvents>
                        <MediaFiles>
                          <MediaFile type="video/mp4" bitrate="800" width="640" height="360">https://cdn/a.mp4</MediaFile>
                        </MediaFiles>
                      </Linear>
                    </Creative>
                  </Creatives>
                </InLine>
              </Ad>
            </VAST>
        """.trimIndent()
        val ads = vastParser.parse(xml, null)
        assertEquals(1, ads.size)
        val ad = ads.single()
        assertEquals("a1", ad.adId)
        assertEquals(1, ad.sequence)
        assertEquals(false, ad.isWrapper)
        assertEquals(20_000L, ad.durationMs)
        assertEquals("https://cdn/a.mp4", ad.mediaFiles.single().uri)
        assertEquals("video/mp4", ad.mediaFiles.single().mimeType)
        assertEquals(800, ad.mediaFiles.single().bitrateKbps)
        assertTrue(ad.trackingEvents.containsKey("start"))
        assertTrue(ad.trackingEvents.containsKey("firstquartile"))
        assertTrue(ad.trackingEvents.containsKey("progress@00:00:05"))
        assertTrue(ad.skipOffset is SkipOffset.Percent)
        assertEquals(10, (ad.skipOffset as SkipOffset.Percent).value)
        assertEquals("https://cdn/simid/index.html", ad.interactiveCreativeUrl)
        assertEquals("SIMID", ad.interactiveApiFramework)
    }

    @Test
    fun `VAST wrong root throws`() {
        assertThrows<VASTParseError> {
            runBlocking {
                vastParser.parse("<AdTagURI>x</AdTagURI>", null)
            }
        }
    }

    @Test
    fun `VAST unsupported version throws`() {
        assertThrows<VASTParseError> {
            runBlocking {
                vastParser.parse(
                    """
                    <VAST version="2.0"><Ad><InLine><Creatives><Creative><Linear>
                      <Duration>00:00:01</Duration>
                      <MediaFiles><MediaFile>https://x</MediaFile></MediaFiles>
                    </Linear></Creative></Creatives></InLine></Ad></VAST>
                    """.trimIndent(),
                    null,
                )
            }
        }
    }

    @Test
    fun `VAST wrapper resolves with fetcher`() = runBlocking {
        val wrapper = """
            <VAST version="3.0">
              <Ad id="wrap">
                <Wrapper>
                  <VASTAdTagURI>https://inner/vast</VASTAdTagURI>
                  <Impression>https://w-imp</Impression>
                </Wrapper>
              </Ad>
            </VAST>
        """.trimIndent()
        val inline = """
            <VAST version="3.0">
              <Ad id="in">
                <InLine>
                  <Impression>https://i-in</Impression>
                  <Creatives><Creative><Linear>
                    <Duration>00:00:15</Duration>
                    <MediaFiles><MediaFile>https://final.mp4</MediaFile></MediaFiles>
                    <TrackingEvents/>
                  </Linear></Creative></Creatives>
                </InLine>
              </Ad>
            </VAST>
        """.trimIndent()
        val fetcher = VASTWrapperFetcher { url ->
            assertEquals("https://inner/vast", url)
            inline
        }
        val ads = VASTPullParser().parse(wrapper, fetcher)
        assertEquals(1, ads.size)
        assertEquals("https://final.mp4", ads.single().mediaFiles.single().uri)
        val imps = ads.single().trackingEvents["impression"].orEmpty()
        assertTrue(imps.any { it.contains("w-imp") } && imps.any { it.contains("i-in") })
    }

    @Test
    fun `VAST wrapper loop throws`() = runBlocking {
        val w = """
            <VAST version="3.0"><Ad><Wrapper>
              <VASTAdTagURI>https://same</VASTAdTagURI>
            </Wrapper></Ad></VAST>
        """.trimIndent()
        val fetcher = VASTWrapperFetcher { w }
        val err = runCatching { vastParser.parse(w, fetcher) }.exceptionOrNull()
        assertTrue(err is VASTParseError)
    }
}
