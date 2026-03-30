package tech.done.ads.player.media3.ima

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject
import tech.done.ads.player.SimidEventListener
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger


class AdDisplayContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val logTag = "SIMID"
    private var simidEventListener: SimidEventListener? = null
    private var currentSimidSessionId: String? = null
    private val outboundMessageId = AtomicInteger(1)

    private val simidWebView: WebView = WebView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        visibility = GONE

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

        addView(simidWebView)
    }

    fun setSimidEventListener(listener: SimidEventListener?) {
        simidEventListener = listener
    }

    fun loadSimidCreative(url: String, sessionId: String) {
        if (url.isBlank()) return
        currentSimidSessionId = sessionId

        simidWebView.setBackgroundColor(Color.TRANSPARENT)
        simidWebView.settings.javaScriptEnabled = true
        simidWebView.settings.domStorageEnabled = true

        simidWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val sid = currentSimidSessionId ?: return
                val args = JSONObject()
                    .put(
                        "environment",
                        JSONObject()
                            .put("sdkName", "AdSDK")
                            .put("platform", "android")
                            .put("api", "webview"),
                    )
                    .put(
                        "player",
                        JSONObject()
                            .put("width", width)
                            .put("height", height),
                    )
                sendSimidMessageInternal(sessionId = sid, type = "init", args = args)
            }
        }

        simidWebView.visibility = VISIBLE
        simidWebView.bringToFront()
        simidWebView.requestFocus()
        simidWebView.loadUrl(url)
    }

    fun hideSimidCreative() {
        currentSimidSessionId = null
        simidWebView.loadUrl("about:blank")
        simidWebView.clearHistory()
        simidWebView.visibility = GONE
    }

    fun sendSimidMessage(type: String, args: String = "{}") {
        val sid = currentSimidSessionId ?: return
        val parsedArgs = runCatching { JSONObject(args) }.getOrNull() ?: JSONObject()
        sendSimidMessageInternal(sessionId = sid, type = type, args = parsedArgs)
    }

    private fun sendSimidMessageInternal(sessionId: String, type: String, args: JSONObject) {
        val msg = JSONObject()
            .put("sessionId", sessionId)
            .put("messageId", outboundMessageId.getAndIncrement())
            .put("timestamp", System.currentTimeMillis())
            .put("type", type)
            .put("args", args)

        post {
            val js = """
                (function(){
                  try {
                    var m = ${JSONObject.quote(msg.toString())};
                    var obj = JSON.parse(m);
                    if (typeof window.onSimidMessage === "function") {
                      window.onSimidMessage(obj);
                    } else if (typeof window.postMessage === "function") {
                      window.postMessage(obj, "*");
                    }
                  } catch (e) {}
                })();
            """.trimIndent()
            simidWebView.evaluateJavascript(js, null)
        }
    }

    inner class SimidBridge {
        @JavascriptInterface
        fun postMessage(message: String) {
            val parsed = runCatching { JSONObject(message) }.getOrNull()
            if (parsed == null) {
                Timber.tag(logTag).w("bridge drop: invalid json len=%s", message.length)
                return
            }

            val sessionId = parsed.optString("sessionId").takeIf { it.isNotBlank() }
                ?: currentSimidSessionId
                ?: "simid-${System.currentTimeMillis()}"

            val messageId = parsed.optInt("messageId", -1).takeIf { it >= 0 } ?: 0
            val timestamp =
                parsed.optLong("timestamp", -1L).takeIf { it >= 0L } ?: System.currentTimeMillis()
            val rawType = parsed.optString("type").orEmpty()
            if (rawType.isBlank()) return

            val normalizedType = rawType
                .removePrefix("SIMID_")
                .trim()

            val args = parsed.optJSONObject("args")
            val msg = SimidMessage(
                sessionId = sessionId,
                messageId = messageId,
                timestamp = timestamp,
                type = normalizedType,
                args = args,
            )

            post {
                currentSimidSessionId = msg.sessionId
                when (msg.type.lowercase()) {
                    "ready" -> {
                        Timber.tag(logTag)
                            .d("ready sessionId=%s messageId=%s", msg.sessionId, msg.messageId)
                        simidEventListener?.onSimidReady(msg.sessionId)
                    }

                    else -> {
                        simidEventListener?.onSimidAction(msg.sessionId, msg.type, msg.args)
                    }
                }
            }
        }
    }
}

