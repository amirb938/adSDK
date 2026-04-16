package tech.done.ads.sample

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tech.done.ads.sample.player.CustomUiVmapScreen
import tech.done.ads.sample.player.SimpleVastScreen
import tech.done.ads.sample.player.SimpleVmapScreen
import tech.done.ads.sample.player.SimidVmapNoSkipScreen
import tech.done.ads.sample.player.SimidVmapScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var destination by remember { mutableStateOf<Destination>(Destination.MainMenu) }
                    var lastBackMs by remember { mutableLongStateOf(0L) }

                    BackHandler(enabled = destination != Destination.MainMenu) {
                        destination = Destination.MainMenu
                    }

                    BackHandler(enabled = destination == Destination.MainMenu) {
                        val now = System.currentTimeMillis()
                        if (now - lastBackMs <= 2_000L) {
                            finish()
                        } else {
                            lastBackMs = now
                            Toast.makeText(this, "برای خروج دوباره بازگشت را بزنید", Toast.LENGTH_SHORT).show()
                        }
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
