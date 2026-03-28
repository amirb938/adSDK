package tech.done.ads.player.media3.ima.internal

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import tech.done.ads.player.media3.R
import tech.done.ads.player.media3.ima.AdSdkUiConfig
import kotlin.math.ceil

internal class AdOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private var lastCanSkip: Boolean = false

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()

    private fun createSkipButtonBackground(
        accentColor: Int?,
        cornerRadiusDp: Int?,
    ): StateListDrawable {
        val accent = accentColor ?: 0xFFFFC107.toInt()
        val radiusPx = dp(cornerRadiusDp ?: 10).toFloat()

        fun shape(
            fillColor: Int,
            strokeWidthDp: Int,
            strokeColor: Int,
        ): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(fillColor)
                setStroke(dp(strokeWidthDp), strokeColor)
            }

        val focused = shape(
            fillColor = 0xAA000000.toInt(),
            strokeWidthDp = 2,
            strokeColor = accent,
        )
        val normal = shape(
            fillColor = 0x88000000.toInt(),
            strokeWidthDp = 1,
            strokeColor = 0x66FFFFFF.toInt(),
        )

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    private val remainingText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        text = ""
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private val skipInText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        text = ""
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
        visibility = INVISIBLE
    }

    private val skipButton = Button(context).apply {
        text = context.getString(R.string.adsdk_skip)
        isAllCaps = false
        visibility = INVISIBLE
        minHeight = dp(36)
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = createSkipButtonBackground(null, null)
    }

    var onSkip: (() -> Unit)? = null
        set(value) {
            field = value
            skipButton.setOnClickListener { field?.invoke() }
        }

    init {
        isClickable = false
        isFocusable = false

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            minimumHeight = dp(56)
            addView(remainingText, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

            val right = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                addView(
                    skipInText,
                    LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                )
                addView(
                    skipButton,
                    LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                )
            }
            addView(
                right,
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            )
        }

        addView(
            row,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            },
        )

        visibility = GONE
    }

    fun applyUiConfig(config: AdSdkUiConfig) {
        config.customTypeface?.let { tf ->
            remainingText.typeface = tf
            skipInText.typeface = tf
            skipButton.typeface = tf
        }
        skipButton.background =
            createSkipButtonBackground(config.accentColor, config.buttonCornerRadiusDp)
    }

    fun setVisible(visible: Boolean) {
        visibility = if (visible) VISIBLE else GONE
    }

    fun render(
        inAd: Boolean,
        adPositionMs: Long,
        adDurationMs: Long?,
        skipOffsetMs: Long?,
    ) {
        setVisible(inAd)
        if (!inAd) {
            lastCanSkip = false
            return
        }

        val canSkip = skipOffsetMs != null && adPositionMs >= skipOffsetMs
        if (skipOffsetMs == null) {
            skipButton.visibility = INVISIBLE
            skipInText.visibility = INVISIBLE
            skipInText.text = ""
        } else {
            skipButton.visibility = if (canSkip) VISIBLE else INVISIBLE
            if (canSkip && !lastCanSkip) {
                skipButton.post {
                    if (visibility == VISIBLE && skipButton.visibility == VISIBLE && !skipButton.hasFocus()) {
                        skipButton.requestFocus()
                    }
                }
            }
            val skipInSec =
                if (!canSkip) {
                    ceil(((skipOffsetMs - adPositionMs).coerceAtLeast(0L)) / 1000.0).toInt()
                } else {
                    null
                }
            if (skipInSec != null) {
                skipInText.visibility = VISIBLE
                skipInText.text =
                    context.getString(
                        R.string.adsdk_ad_skip_in_seconds,
                        skipInSec,
                    )
            } else {
                skipInText.visibility = INVISIBLE
                skipInText.text = ""
            }
        }
        lastCanSkip = canSkip

        val remainingSec = adDurationMs?.let { dur ->
            ceil(((dur - adPositionMs).coerceAtLeast(0L)) / 1000.0).toInt()
        }

        remainingText.text =
            remainingSec?.let {
                context.getString(
                    R.string.adsdk_ad_remaining_seconds,
                    it,
                )
            }.orEmpty()
    }
}
