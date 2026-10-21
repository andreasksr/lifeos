package app.lifeos

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import app.lifeos.ui.LockScreen
import androidx.fragment.app.FragmentActivity
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumTheme
import app.lifeos.ui.LifeOsShell
import app.lifeos.ui.RecoveryScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Einzige Activity. FragmentActivity, damit die biometrische App-Sperre (BiometricPrompt) möglich ist.
 * Öffnet Einträge aus Benachrichtigungen und Widget über [EXTRA_OPEN] („task:<id>", „event:<id>", …).
 */
class MainActivity : FragmentActivity() {

    /** Zu öffnendes Ziel (aus Benachrichtigung/Widget) – die Shell navigiert und setzt es zurück. */
    val pendingOpen = MutableStateFlow<String?>(null)

    private var container: AppContainer? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container?.notificationAccess?.let { access ->
            access.refresh()
            if (!granted) access.openSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handle(intent)
        val app = application as LifeOsApplication
        val systemAnimationsOff = Settings.Global.getFloat(
            contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f

        setContent {
            var start by remember { mutableStateOf<StartResult?>(null) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                val r = app.start()
                if (r is StartResult.Ready) attach(r.container)
                start = r
            }

            when (val s = start) {
                null -> ContinuumTheme(motionEnabled = false) {
                    Box(Modifier.fillMaxSize().background(ContinuumColors.Obsidian))
                }
                is StartResult.KeyLost -> ContinuumTheme(motionEnabled = false) {
                    Box(Modifier.fillMaxSize().background(ContinuumColors.Obsidian)) {
                        RecoveryScreen(
                            reason = s.reason,
                            onWipe = { scope.launch { start = app.wipeAndRestart() } },
                        )
                    }
                }
                is StartResult.Ready -> {
                    val lockEnabled by s.container.settings.appLockEnabled.collectAsStateWithLifecycle(initialValue = null)
                    val locked by s.container.appLock.locked.collectAsStateWithLifecycle()
                    when {
                        lockEnabled == null -> ContinuumTheme(motionEnabled = false) { Box(Modifier.fillMaxSize().background(ContinuumColors.Obsidian)) }
                        lockEnabled == true && locked -> ContinuumTheme(motionEnabled = false) { LockScreen(onUnlocked = s.container.appLock::unlock) }
                        else -> LifeOsShell(s.container, systemAnimationsOff, pendingOpen)
                    }
                }
            }
        }
    }

    private fun attach(c: AppContainer) {
        container = c
        c.notificationAccess.requester = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                c.notificationAccess.openSettings()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container?.let { c -> lifecycleScope.launch { c.appLock.onForeground(c.settings.appLockEnabled.first()) } }
    }

    override fun onStop() {
        container?.appLock?.onBackground()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        container?.notificationAccess?.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN)?.let { pendingOpen.value = it }
    }

    override fun onDestroy() {
        container?.notificationAccess?.requester = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN = "lifeos.open"
    }
}
