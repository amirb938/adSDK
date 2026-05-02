package tech.done.ads.player.media3.ima.internal

import android.content.res.ColorStateList
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.text.TextUtils
import android.view.Gravity
import android.view.View
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

    private fun createRoundedPill(
        fillColor: Int,
        strokeWidthDp: Int = 0,
        strokeColor: Int = Color.TRANSPARENT,
        cornerRadiusDp: Int = 40,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerRadiusDp).toFloat()
            setColor(fillColor)
            if (strokeWidthDp > 0) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }

    private fun createSkipButtonBackground(
        accentColor: Int?,
        cornerRadiusDp: Int?,
    ): StateListDrawable {
        val accent = accentColor ?: 0xFF111111.toInt()
        val radiusDp = cornerRadiusDp ?: 40
        fun shape(
            fillColor: Int,
            strokeWidthDp: Int,
            strokeColor: Int,
        ): GradientDrawable =
            createRoundedPill(
                fillColor = fillColor,
                strokeWidthDp = strokeWidthDp,
                strokeColor = strokeColor,
                cornerRadiusDp = radiusDp,
            )

        val focused = shape(
            fillColor = Color.WHITE,
            strokeWidthDp = 2,
            strokeColor = accent,
        )
        val normal = shape(
            fillColor = Color.WHITE,
            strokeWidthDp = 1,
            strokeColor = 0x33FFFFFF,
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
        gravity = Gravity.CENTER
        visibility = INVISIBLE
        minWidth = dp(68)
        minHeight = dp(40)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = createRoundedPill(fillColor = 0x99000000.toInt(), cornerRadiusDp = 40)
    }

    private val circularTimerView = CircularTimerView(context).apply {
        visibility = INVISIBLE
    }

    private val skipButton = Button(context).apply {
        text = context.getString(R.string.adsdk_skip_ad)
        setTextColor(
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf()
                ),
                intArrayOf(
                    Color.BLACK,
                    0x66000000
                )
            )
        )
        isAllCaps = false
        visibility = GONE
        minWidth = dp(72)
        minHeight = dp(40)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(dp(16), dp(10), dp(16), dp(10))
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
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                skipButton,
                LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                circularTimerView,
                LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginEnd = dp(6)
                },
            )

            addView(
                skipInText,
                LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginEnd = dp(6)
                },
            )
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            minimumHeight = dp(56)
            if (isRtl) {
                addView(
                    controls,
                    LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
                )
                addView(remainingText, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            } else {

                addView(remainingText, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(
                    controls,
                    LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
                )
            }
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
            circularTimerView.typeface = tf
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
        val remainingSec = adDurationMs?.let { dur ->
            ceil(((dur - adPositionMs).coerceAtLeast(0L)) / 1000.0).toInt()
        }

        if (skipOffsetMs == null) {
            skipButton.visibility = GONE
            skipInText.visibility = GONE
            skipInText.text = ""
            circularTimerView.visibility = VISIBLE
        } else {
            skipButton.visibility = if (canSkip) VISIBLE else GONE
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
                skipInText.text = context.getString(
                    R.string.adsdk_ad_skip_in_seconds,
                    skipInSec,
                )
                circularTimerView.visibility = GONE
            } else {
                skipInText.visibility = GONE
                skipInText.text = ""
                circularTimerView.visibility = VISIBLE
            }
        }
        lastCanSkip = canSkip
        circularTimerView.setTimeText(remainingSec)
        circularTimerView.setProgress(adPositionMs, adDurationMs)

        remainingText.text = ""
    }

    private class CircularTimerView(context: Context) : FrameLayout(context) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(context, 2).toFloat()
            color = Color.WHITE
            strokeCap = Paint.Cap.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x99000000.toInt()
        }
        private val arcRect = RectF()
        private val label = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        private var progressFraction: Float = 0f

        var typeface = label.typeface
            set(value) {
                field = value
                label.typeface = value
            }

        init {
            setWillNotDraw(false)
            addView(
                label,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
            )
        }

        fun setTimeText(seconds: Int?) {
            label.text = seconds?.toString().orEmpty()
        }

        fun setProgress(positionMs: Long, durationMs: Long?) {
            progressFraction =
                if (durationMs == null || durationMs <= 0L) {
                    0f
                } else {
                    (positionMs.coerceAtLeast(0L).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                }
            invalidate()
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = (minOf(width, height) / 2f) - ringPaint.strokeWidth
            canvas.drawCircle(cx, cy, radius, fillPaint)
            arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arcRect, -90f, 360f * progressFraction, false, ringPaint)
        }

        companion object {
            private fun dp(context: Context, value: Int): Int =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value.toFloat(),
                    context.resources.displayMetrics,
                ).toInt()
        }
    }
}
