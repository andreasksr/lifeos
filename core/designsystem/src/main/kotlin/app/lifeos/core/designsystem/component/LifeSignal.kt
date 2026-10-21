package app.lifeos.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import kotlinx.coroutines.delay

data class LifeToastMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val tone: NodeTone = NodeTone.ACTIVE,
    val id: Long = System.nanoTime(),
)

/**
 * App-weite Rückmeldungen: Lichtimpuls entlang der LifeLine ([pulse]) und kurze Bestätigungen mit
 * optionalem „Rückgängig" ([toast]). Jede gespeicherte Eingabe wird so eindeutig bestätigt.
 */
@Stable
class LifeSignalState {
    var pulseCount by mutableIntStateOf(0)
        private set
    var toast by mutableStateOf<LifeToastMessage?>(null)
        private set

    fun pulse() {
        pulseCount++
    }

    fun toast(message: LifeToastMessage) {
        toast = message
    }

    fun confirm(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        pulse()
        toast(LifeToastMessage(text, actionLabel, onAction))
    }

    fun dismissToast(id: Long) {
        if (toast?.id == id) toast = null
    }
}

val LocalLifeSignal = staticCompositionLocalOf { LifeSignalState() }

/** Anzeige der aktuellen Bestätigung – wird vom LifeFrame über der Navigation platziert. */
@Composable
fun LifeToastHost(state: LifeSignalState, modifier: Modifier = Modifier) {
    val current = state.toast
    LaunchedEffect(current?.id) {
        val id = current?.id ?: return@LaunchedEffect
        delay(if (current.actionLabel != null) 8_000 else 3_200)
        state.dismissToast(id)
    }
    val motion = Continuum.motion
    AnimatedVisibility(
        visible = current != null,
        modifier = modifier,
        enter = slideInVertically(tween(motion.standardMillis)) { it } + fadeIn(tween(motion.standardMillis)),
        exit = slideOutVertically(tween(motion.quickMillis)) { it } + fadeOut(tween(motion.quickMillis)),
    ) {
        val msg = current ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ContinuumSpacing.gutter)
                .heightIn(min = 52.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .drawBehind {
                    drawRect(ContinuumColors.Elevated)
                    drawLine(ContinuumColors.Signature, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.5.dp.toPx())
                }
                .padding(start = ContinuumSpacing.l, end = ContinuumSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LifeNode(shape = NodeShape.DIAMOND, tone = msg.tone, active = true, size = 10.dp)
            Spacer(Modifier.width(12.dp))
            Text(msg.text, style = Continuum.type.body, modifier = Modifier.weight(1f), maxLines = 2)
            if (msg.actionLabel != null && msg.onAction != null) {
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) {
                            msg.onAction.invoke()
                            state.dismissToast(msg.id)
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(msg.actionLabel.uppercase(), style = Continuum.type.label, color = ContinuumColors.Cyan)
                }
            }
        }
    }
}
