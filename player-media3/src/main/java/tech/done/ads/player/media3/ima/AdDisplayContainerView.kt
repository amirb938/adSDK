package tech.done.ads.player.media3.ima

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import org.json.JSONObject
import timber.log.Timber


class AdDisplayContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    interface SimidEventListener {
        fun onRequestPause()
        fun onRequestPlay()
    }

    private var simidEventListener: SimidEventListener? = null

    private val simidWebView: WebView = WebView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(Color.TRANSPARENT)
        visibility = View.GONE

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message().orEmpty()
                val line = consoleMessage?.lineNumber()
                val src = consoleMessage?.sourceId().orEmpty()
                Timber.tag("SIMID_JS").d("console line=%s source=%s msg=%s", line, src, msg)
                return super.onConsoleMessage(consoleMessage)
            }
        }

        isFocusableInTouchMode = true
        addJavascriptInterface(SimidBridge(), "AndroidSimidBridge")
    }

    init {
        clipChildren = false
        clipToPadding = false

        // The ad player view (and other overlays) are added elsewhere.
        // This WebView is always present but only made visible when a SIMID creative is loaded.
        addView(simidWebView)
    }

    fun setSimidEventListener(listener: SimidEventListener?) {
        simidEventListener = listener
    }

    fun loadSimidCreative(url: String) {
        if (url.isBlank()) return
        simidWebView.visibility = View.VISIBLE
        simidWebView.bringToFront()
        simidWebView.requestFocus()
        simidWebView.loadUrl(url)
    }

    fun hideSimidCreative() {
        simidWebView.loadUrl("about:blank")
        simidWebView.clearHistory()
        simidWebView.visibility = View.GONE
    }

    inner class SimidBridge {
        @JavascriptInterface
        fun postMessage(message: String) {
            val type = runCatching {
                JSONObject(message).optString("type").takeIf { it.isNotBlank() }
            }.getOrNull() ?: return

            // WebView -> bridge calls may arrive off the main thread; hop to the view thread.
            post {
                when (type) {
                    "SIMID_requestPause" -> simidEventListener?.onRequestPause()
                    "SIMID_requestPlay" -> simidEventListener?.onRequestPlay()
                }
            }
        }
    }
}

