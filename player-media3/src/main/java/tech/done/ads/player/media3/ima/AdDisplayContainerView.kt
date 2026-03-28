package tech.done.ads.player.media3.ima

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout


class AdDisplayContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    init {
        clipChildren = false
        clipToPadding = false
    }
}

