package tech.done.adsdk.player.media3.ima

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * IMA-like ad display container.
 *
 * The app places this view on top of its content player view (e.g., in a FrameLayout/Box).
 * The SDK uses this container to render ad video/UI.
 */
class AdDisplayContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr)

