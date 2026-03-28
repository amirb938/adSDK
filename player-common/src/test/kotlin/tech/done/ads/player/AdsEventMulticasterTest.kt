package tech.done.ads.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdsEventMulticasterTest {

    @Test
    fun fansOutToAllListeners() {
        val m = AdsEventMulticaster()
        var a = 0
        var b = 0
        m.addListener(object : AdsEventListener {
            override fun onVMAPParsed(version: String?, adBreakCount: Int) {
                a++
            }
        })
        m.addListener(object : AdsEventListener {
            override fun onVMAPParsed(version: String?, adBreakCount: Int) {
                b++
            }
        })
        m.onVMAPParsed("1.0", 3)
        assertEquals(1, a)
        assertEquals(1, b)
    }

    @Test
    fun clearRemovesListeners() {
        val m = AdsEventMulticaster()
        var c = 0
        m.addListener(object : AdsEventListener {
            override fun onAdStarted(breakId: String?) {
                c++
            }
        })
        m.clear()
        m.onAdStarted("x")
        assertEquals(0, c)
    }
}
