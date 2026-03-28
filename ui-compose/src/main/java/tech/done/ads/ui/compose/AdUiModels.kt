package tech.done.ads.ui.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class AdUiState(
    val visible: Boolean = false,
    val canSkip: Boolean = false,
    val skipInSeconds: Int? = null,
    val remainingSeconds: Int? = null,
    val adIndex: Int? = null,
    val adCount: Int? = null,
)

@Immutable
data class AdUiStyle(
    val scrimColor: Color = Color(0x66000000),
    val textColor: Color = Color.White,
    val accentColor: Color = Color(0xFF00C853),
)

