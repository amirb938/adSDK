package tech.done.adsdk.player.media3.ima

import android.graphics.Typeface
import androidx.annotation.ColorInt

data class AdSdkUiConfig(
    val customTypeface: Typeface? = null,
    @ColorInt val accentColor: Int? = null,
    val buttonCornerRadiusDp: Int? = null,
)
