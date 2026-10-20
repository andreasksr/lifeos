package app.lifeos

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumTheme
import app.lifeos.ui.LifeOsShell
import app.lifeos.ui.RecoveryScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as LifeOsApplication
        val systemAnimationsOff = Settings.Global.getFloat(
            contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f

        setContent {
            var start by remember { mutableStateOf<StartResult?>(null) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { start = app.start() }

            when (val s = start) {
                null -> ContinuumTheme(motionEnabled = false) {
                    Box(Modifier.fillMaxSize().background(ContinuumColors.Obsidian))
                }
                is StartResult.KeyLost -> ContinuumTheme(motionEnabled = false) {
                    RecoveryScreen(
                        reason = s.reason,
                        onWipe = { scope.launch { start = app.wipeAndRestart() } },
                    )
                }
                is StartResult.Ready -> LifeOsShell(s.container, systemAnimationsOff)
            }
        }
    }
}
