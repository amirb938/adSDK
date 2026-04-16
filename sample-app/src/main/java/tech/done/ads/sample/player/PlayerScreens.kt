package tech.done.ads.sample.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SimpleVastScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerScenarioScreen(
        modifier = modifier,
        onBack = onBack,
        scenario = PlayerScenario.SimpleVast,
    )
}

@Composable
fun SimpleVmapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerScenarioScreen(
        modifier = modifier,
        onBack = onBack,
        scenario = PlayerScenario.SimpleVmap,
    )
}

@Composable
fun CustomUiVmapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerScenarioScreen(
        modifier = modifier,
        onBack = onBack,
        scenario = PlayerScenario.CustomUiVmap,
    )
}

@Composable
fun SimidVmapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerScenarioScreen(
        modifier = modifier,
        onBack = onBack,
        scenario = PlayerScenario.SimidVmap,
    )
}

@Composable
fun SimidVmapNoSkipScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerScenarioScreen(
        modifier = modifier,
        onBack = onBack,
        scenario = PlayerScenario.SimidVmapNoSkip,
    )
}

