package app.lifeos.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors

/** Was die Linie in diesem Kontext bedeutet – steuert Farbe und Richtung des Lichts. */
enum class LineMeaning {
    /** Zeit / neutraler Verlauf: Cyan → Violett. */
    TIME,

    /** Geld fließt zu: Mint. */
    INFLOW,

    /** Geld fließt ab: Violett. */
    OUTFLOW,

    /** Fortschritt / Auslastung: Verlauf; Überschreitung wird jenseits der 100-%-Marke in Alert gezeigt. */
    PROGRESS,

    /** Erledigt / positiv abgeschlossen: Mint. */
    DONE,
}

internal fun lineBrush(meaning: LineMeaning): Brush = when (meaning) {
    LineMeaning.INFLOW -> Brush.horizontalGradient(listOf(ContinuumColors.Mint.copy(alpha = 0.5f), ContinuumColors.Mint))
    LineMeaning.OUTFLOW -> Brush.horizontalGradient(listOf(ContinuumColors.Violet.copy(alpha = 0.5f), ContinuumColors.Violet))
    LineMeaning.DONE -> Brush.horizontalGradient(listOf(ContinuumColors.Cyan, ContinuumColors.Mint))
    LineMeaning.TIME, LineMeaning.PROGRESS -> ContinuumColors.Signature
}

/**
 * Die LifeLine als Messlinie.
 *
 * - `progress == null`: volle Linie (Trenner/Zeitachse), optional mit wanderndem Lichtpuls.
 * - `progress in 0..1`: leuchtendes Segment auf einer Haarlinie.
 * - `progress > 1`: **Überschreitung wird nicht abgeschnitten.** Die Linie wird so skaliert, dass die
 *   100-%-Marke bei `1/progress` der Breite liegt; der Teil darüber hinaus erscheint in Alert.
 *
 * Zeichnet ausschließlich im Draw-Pass: keine Recomposition pro Animationsframe.
 */
@Composable
fun LifeLine(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    meaning: LineMeaning = LineMeaning.TIME,
    pulse: Boolean = false,
    progressDescription: String? = null,
    thickness: Dp = 2.dp,
) {
    val motion = Continuum.motion
    val target = progress?.coerceAtLeast(0f) ?: 1f
    val animated by animateFloatAsState(target, tween(motion.emphasisMillis), label = "lifeLine")
    val pulseState: State<Float> = if (pulse && motion.enabled) {
        rememberInfiniteTransition(label = "lifeLinePulse").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(motion.pulseMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "pulse",
        )
    } else {
        remember { mutableFloatStateOf(-1f) }
    }
    val semanticsModifier = if (progress != null) {
        Modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            if (progressDescription != null) stateDescription = progressDescription
        }
    } else {
        Modifier
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(thickness * 5)
            .then(semanticsModifier),
    ) {
        drawMeasureLine(animated, meaning, pulseState.value, showTrack = progress != null, thickness.toPx())
    }
}

internal fun DrawScope.drawMeasureLine(value: Float, meaning: LineMeaning, pulseAt: Float, showTrack: Boolean, stroke: Float) {
    val y = size.height / 2f
    val w = size.width
    if (showTrack) {
        drawLine(ContinuumColors.Hairline, Offset(0f, y), Offset(w, y), strokeWidth = stroke * 0.6f, cap = StrokeCap.Round)
    }
    val overflow = value > 1f
    val limitX = if (overflow) w / value else w * value.coerceIn(0f, 1f)
    if (limitX > 0f) {
        val brush = lineBrush(meaning)
        drawLine(brush, Offset(0f, y), Offset(limitX, y), strokeWidth = stroke * 3.5f, cap = StrokeCap.Round, alpha = 0.10f)
        drawLine(brush, Offset(0f, y), Offset(limitX, y), strokeWidth = stroke, cap = StrokeCap.Round)
    }
    if (overflow) {
        drawLine(ContinuumColors.Alert.copy(alpha = 0.15f), Offset(limitX, y), Offset(w, y), strokeWidth = stroke * 3.5f, cap = StrokeCap.Round)
        drawLine(ContinuumColors.Alert, Offset(limitX, y), Offset(w, y), strokeWidth = stroke, cap = StrokeCap.Round)
        // 100-%-Marke
        drawLine(ContinuumColors.Frost, Offset(limitX, y - stroke * 2.2f), Offset(limitX, y + stroke * 2.2f), strokeWidth = stroke * 0.8f)
    } else if (showTrack && limitX > 0f) {
        drawCircle(ContinuumColors.Frost, radius = stroke * 1.25f, center = Offset(limitX, y))
    }
    if (pulseAt in 0f..1f) {
        val end = if (overflow) w else limitX
        val x = end * pulseAt
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.85f), ContinuumColors.Cyan.copy(alpha = 0f)),
                center = Offset(x, y), radius = stroke * 6f,
            ),
            radius = stroke * 6f, center = Offset(x, y),
        )
    }
}
