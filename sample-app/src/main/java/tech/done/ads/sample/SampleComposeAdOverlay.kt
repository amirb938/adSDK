package tech.done.ads.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tech.done.ads.player.PlayerState
import tech.done.ads.ui.compose.AdOverlay
import tech.done.ads.ui.compose.AdUiState
import tech.done.ads.ui.compose.AdUiStyle
import kotlin.math.ceil

internal fun playerStateToAdUiState(ps: PlayerState): AdUiState {
    if (!ps.isInAd) return AdUiState(visible = false)
    val pos = ps.adPositionMs
    val dur = ps.adDurationMs
    val remainingSeconds =
        dur?.let { ceil(((it - pos).coerceAtLeast(0L)) / 1000.0).toInt() }
    if (!ps.isAdSkippable) {
        return AdUiState(
            visible = true,
            canSkip = false,
            skipInSeconds = null,
            remainingSeconds = remainingSeconds,
            adIndex = null,
            adCount = null,
        )
    }
    val skipOffset = ps.adSkipOffsetMs
    val canSkip = skipOffset != null && pos >= skipOffset
    val skipInSeconds =
        if (!canSkip && skipOffset != null) {
            ceil(((skipOffset - pos).coerceAtLeast(0L)) / 1000.0).toInt()
        } else {
            null
        }
    return AdUiState(
        visible = true,
        canSkip = canSkip,
        skipInSeconds = skipInSeconds,
        remainingSeconds = remainingSeconds,
        adIndex = null,
        adCount = null,
    )
}

@Composable
internal fun SampleCustomAdOverlay(
    playerState: PlayerState,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adUi = playerStateToAdUiState(playerState)
    val latestPlayerState = rememberUpdatedState(playerState)
    val latestOnSkip = rememberUpdatedState(onSkip)
    val style = AdUiStyle(
        scrimColor = Color(0x991B5E20),
        textColor = Color(0xFFE8F5E9),
        accentColor = Color(0xFFFFAB00),
    )
    AdOverlay(
        state = adUi,
        modifier = modifier,
        style = style,
        onSkip = onSkip,
        overrideContent = { s ->
            val skipFocusRequester = remember { FocusRequester() }
            LaunchedEffect(s.visible, s.canSkip, s.skipInSeconds) {
                if (!s.visible) return@LaunchedEffect
                if (!s.canSkip && s.skipInSeconds == null) return@LaunchedEffect
                delay(1)
                skipFocusRequester.requestFocus()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .focusGroup()
                        .background(style.accentColor.copy(alpha = 0.92f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = s.remainingSeconds?.let { "Sponsored · $it s left" } ?: "Sponsored",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B5E20),
                    )
                    if (s.canSkip || s.skipInSeconds != null) {
                        val skipLabel = when {
                            s.canSkip -> "Skip"
                            s.skipInSeconds != null -> "Skip in ${s.skipInSeconds}s"
                            else -> "Skip"
                        }
                        val skipContentColor =
                            if (s.canSkip) Color(0xFF1B5E20) else Color(0xFF1B5E20).copy(alpha = 0.55f)
                        TextButton(
                            onClick = {
                                if (playerStateToAdUiState(latestPlayerState.value).canSkip) {
                                    latestOnSkip.value()
                                }
                            },
                            enabled = true,
                            modifier = Modifier
                                .focusRequester(skipFocusRequester)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = skipContentColor),
                        ) {
                            Text(skipLabel, color = skipContentColor)
                        }
                    }
                }
            }
        },
    )
}
