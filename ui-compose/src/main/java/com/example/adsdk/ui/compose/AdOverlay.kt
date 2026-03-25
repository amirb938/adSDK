package com.example.adsdk.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AdOverlay(
    state: AdUiState,
    modifier: Modifier = Modifier,
    style: AdUiStyle = AdUiStyle(),
    onSkip: () -> Unit = {},
    overrideContent: (@Composable (AdUiState) -> Unit)? = null,
) {
    if (!state.visible) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(style.scrimColor),
    ) {
        if (overrideContent != null) {
            overrideContent(state)
            return@Box
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val adCountText = when {
                state.adIndex != null && state.adCount != null -> "Ad ${state.adIndex} of ${state.adCount}"
                state.adIndex != null -> "Ad ${state.adIndex}"
                else -> "Ad"
            }
            Text(
                text = adCountText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = style.textColor,
            )

            val countdownText = state.remainingSeconds?.let { "Ends in ${it}s" }
            if (countdownText != null) {
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = style.textColor,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val skipLabel = when {
                state.canSkip -> "Skip"
                state.skipInSeconds != null -> "Skip in ${state.skipInSeconds}s"
                else -> "Skip"
            }

            Button(
                onClick = onSkip,
                enabled = state.canSkip,
                colors = ButtonDefaults.buttonColors(containerColor = style.accentColor),
            ) {
                Text(text = skipLabel, color = style.textColor)
            }
            Spacer(modifier = Modifier.width(0.dp))
        }
    }
}

