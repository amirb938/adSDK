package tech.done.ads.sample

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MainMenuScreen(
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "AdSDK Sample",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Choose a scenario to test VAST/VMAP playback, custom ad UI, and SIMID overlays.",
            style = MaterialTheme.typography.bodyMedium,
        )

        MenuButton(
            text = "Simple Player + VAST Ad",
            onClick = { onNavigate(Destination.SimpleVast) },
            modifier = Modifier.focusRequester(firstFocus),
        )
        MenuButton(
            text = "Simple Player + VMAP Ad",
            onClick = { onNavigate(Destination.SimpleVmap) },
        )
        MenuButton(
            text = "Player + Custom Ad UI (VMAP)",
            onClick = { onNavigate(Destination.CustomUiVmap) },
        )
        MenuButton(
            text = "Player + SIMID Overlay (VMAP)",
            onClick = { onNavigate(Destination.SimidVmap) },
        )
        MenuButton(
            text = "Player + SIMID QR (No-Skip VMAP)",
            onClick = { onNavigate(Destination.SimidVmapNoSkip) },
        )
        MenuButton(
            text = "External Player Activity (createWithExternalPlayer)",
            onClick = { context.startActivity(android.content.Intent(context, ExternalPlayerActivity::class.java)) },
        )
    }
}

@Composable
private fun MenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val focusBorder = if (isFocused) Color(0xFFFFC107) else Color.Transparent
    val shape = MaterialTheme.shapes.medium

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(width = 3.dp, color = focusBorder, shape = shape),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Color(0xFF1E293B) else MaterialTheme.colorScheme.primary,
            contentColor = if (isFocused) Color.White else MaterialTheme.colorScheme.onPrimary,
        ),
        interactionSource = interactionSource,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

