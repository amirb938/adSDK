package tech.done.adsdk.player.media3.ima.internal

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.ceil

internal class AdOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val countdownText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        text = ""
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private val skipButton = Button(context).apply {
        text = context.getString(tech.done.adsdk.player.media3.R.string.adsdk_skip)
        isAllCaps = false
        visibility = View.GONE
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
            setPadding(24, 24, 24, 24)
            addView(countdownText, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(skipButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        addView(
            row,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            },
        )

        visibility = View.GONE
    }

    fun setVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun render(
        inAd: Boolean,
        adPositionMs: Long,
        adDurationMs: Long?,
        skipOffsetMs: Long = 3_000L,
    ) {
        setVisible(inAd)
        if (!inAd) return

        val canSkip = adPositionMs >= skipOffsetMs
        skipButton.visibility = if (canSkip) View.VISIBLE else View.GONE

        val remainingSec = adDurationMs?.let { dur ->
            ceil(((dur - adPositionMs).coerceAtLeast(0L)) / 1000.0).toInt()
        }

        val skipInSec = if (canSkip) null else ceil(((skipOffsetMs - adPositionMs).coerceAtLeast(0L)) / 1000.0).toInt()

        countdownText.text = buildString {
            if (remainingSec != null) {
                append(
                    context.getString(
                        tech.done.adsdk.player.media3.R.string.adsdk_ad_remaining_seconds,
                        remainingSec,
                    ),
                )
            }
            if (skipInSec != null) {
                if (isNotEmpty()) append("  ")
                append(
                    context.getString(
                        tech.done.adsdk.player.media3.R.string.adsdk_ad_skip_in_seconds,
                        skipInSec,
                    ),
                )
            }
        }
    }
}

