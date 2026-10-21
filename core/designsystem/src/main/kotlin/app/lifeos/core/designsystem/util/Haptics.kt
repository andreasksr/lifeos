package app.lifeos.core.designsystem.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Dezentes haptisches Feedback für wichtige Interaktionen. Nutzt die System-Konstanten, damit die
 * Android-Einstellung „Vibration bei Berührung" respektiert wird.
 */
class LifeHaptics(private val view: View) {
    /** Leichter Impuls – Auswahl, Knoten antippen. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Bestätigung – etwas wurde gespeichert. */
    fun confirm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /** Ablehnung – Eingabe ungültig. */
    fun reject() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}

@Composable
fun rememberLifeHaptics(): LifeHaptics {
    val view = LocalView.current
    return remember(view) { LifeHaptics(view) }
}
