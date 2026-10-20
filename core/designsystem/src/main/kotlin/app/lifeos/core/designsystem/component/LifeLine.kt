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
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors

/** Was die Linie in diesem Kontext bedeutet – steuert Farbe und Richtung des Lichts. */
enum class LineMeaning {
    /** Zeit / neutraler Verlauf: Cyan → Violett. */
    TIME,

    /** Geld fließt zu: Cyan-betont. */
    INFLOW,

    /** Geld fließt ab: Violett-betont. */
    OUTFLOW,

    /** Fortschritt / Auslastung: Verlauf; über 100 % wechselt das Ende zu Flare. */
    PROGRESS,
}

internal fun lineBrush(meaning: LineMeaning, overflow: Boolean = false): Brush = when {
    overflow -> Brush.horizontalGradient(listOf(ContinuumColors.Violet, ContinuumColors.Flare))
    meaning == LineMeaning.INFLOW -> Brush.horizontalGradient(listOf(ContinuumColors.Cyan.copy(alpha = 0.55f), ContinuumColors.Cyan))
    meaning == LineMeaning.OUTFLOW -> Brush.horizontalGradient(listOf(ContinuumColors.Violet.copy(alpha = 0.55f), ContinuumColors.Violet))
    else -> ContinuumColors.Signature
}

/**
 * Die LifeLine – zentrales Gestaltungselement von CONTINUUM.
 *
 * Eine feine Haarlinie über die volle Breite; darauf ein leuchtendes Segment für [progress]
 * (0..1, `null` = volle Linie ohne Messwert). Optional wandert ein Lichtpuls entlang der Linie –
 * nur wenn Bewegung aktiv ist. Werte > 1 werden als Überschreitung dargestellt.
 *
 * Zeichnet ausschließlich im Draw-Pass: keine Recomposition pro Frame.
 */
@Composable
fun LifeLine(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    meaning: LineMeaning = LineMeaning.TIME,
    pulse: Boolean = false,
    progressDescription: String? = null,
) {
    val motion = Continuum.motion
    val target = progress?.coerceAtLeast(0f) ?: 1f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(motion.emphasisMillis),
        label = "lifeLineProgress",
    )
    val pulseState: State<Float> = if (pulse && motion.enabled) {
        rememberInfiniteTransition(label = "lifeLinePulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
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
            .height(10.dp)
            .then(semanticsModifier),
    ) {
        drawLifeLine(
            fraction = animated,
            meaning = meaning,
            pulseAt = pulseState.value,
            showTrack = progress != null,
        )
    }
}

private fun DrawScope.drawLifeLine(fraction: Float, meaning: LineMeaning, pulseAt: Float, showTrack: Boolean) {
    val y = size.height / 2f
    val stroke = 1.5.dp.toPx()
    val overflow = fraction > 1f
    val visible = fraction.coerceIn(0f, 1f)

    if (showTrack) {
        drawLine(ContinuumColors.Hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
    if (visible > 0f) {
        val end = Offset(size.width * visible, y)
        val brush = lineBrush(meaning, overflow)
        // Glow: breite, transparente Linie unter der scharfen Linie
        drawLine(brush, Offset(0f, y), end, strokeWidth = stroke * 4f, cap = StrokeCap.Round, alpha = 0.12f)
        drawLine(brush, Offset(0f, y), end, strokeWidth = stroke, cap = StrokeCap.Round)
        // Endpunkt als präzise Markierung
        if (showTrack) {
            drawCircle(if (overflow) ContinuumColors.Flare else ContinuumColors.Ice, radius = stroke * 1.4f, center = end)
        }
    }
    if (pulseAt in 0f..1f && visible > 0f) {
        val x = size.width * visible * pulseAt
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.9f), ContinuumColors.Cyan.copy(alpha = 0f)),
                center = Offset(x, y),
                radius = stroke * 5f,
            ),
            radius = stroke * 5f,
            center = Offset(x, y),
        )
    }
}
