package tech.done.ads.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var destination by remember { mutableStateOf<Destination>(Destination.MainMenu) }

                    BackHandler(enabled = destination != Destination.MainMenu) {
                        destination = Destination.MainMenu
                    }

                    when (destination) {
                        Destination.MainMenu -> MainMenuScreen(onNavigate = { destination = it })
                        Destination.SimpleVast -> SimpleVastScreen(onBack = { destination = Destination.MainMenu })
                        Destination.SimpleVmap -> SimpleVmapScreen(onBack = { destination = Destination.MainMenu })
                        Destination.CustomUiVmap -> CustomUiVmapScreen(onBack = { destination = Destination.MainMenu })
                        Destination.SimidVmap -> SimidVmapScreen(onBack = { destination = Destination.MainMenu })
                        Destination.SimidVmapNoSkip -> SimidVmapNoSkipScreen(onBack = { destination = Destination.MainMenu })
                    }
                }
            }
        }
    }
}
