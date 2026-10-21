package app.lifeos.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors

/**
 * LifeProgress – kreisförmige Fortschrittslinie. Werte > 1 laufen als zweite Runde in Alert weiter
 * (die Überschreitung bleibt sichtbar statt bei 100 % abgeschnitten zu werden).
 * [center] zeigt typischerweise den Prozentwert oder einen Countdown.
 */
@Composable
fun LifeProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    meaning: LineMeaning = LineMeaning.PROGRESS,
    description: String? = null,
    center: (@Composable () -> Unit)? = null,
) {
    val animated by animateFloatAsState(progress.coerceAtLeast(0f), tween(Continuum.motion.emphasisMillis), label = "ring")
    Box(
        modifier
            .size(size)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                if (description != null) stateDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = 2.5.dp.toPx()
            val inset = stroke * 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(ContinuumColors.Hairline, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val first = animated.coerceAtMost(1f)
            if (first > 0f) {
                val brush = when (meaning) {
                    LineMeaning.DONE, LineMeaning.INFLOW -> Brush.sweepGradient(listOf(ContinuumColors.Cyan, ContinuumColors.Mint, ContinuumColors.Cyan))
                    LineMeaning.OUTFLOW -> Brush.sweepGradient(listOf(ContinuumColors.Violet, ContinuumColors.Cyan, ContinuumColors.Violet))
                    else -> Brush.sweepGradient(listOf(ContinuumColors.Cyan, ContinuumColors.Violet, ContinuumColors.Cyan))
                }
                drawArc(brush, -90f, 360f * first, false, topLeft, arcSize, alpha = 0.18f, style = Stroke(stroke * 4f))
                drawArc(brush, -90f, 360f * first, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (animated > 1f) {
                val over = (animated - 1f).coerceAtMost(1f)
                val innerInset = inset + stroke * 2.4f
                drawArc(
                    ContinuumColors.Alert, -90f, 360f * over, false,
                    Offset(innerInset, innerInset),
                    Size(this.size.width - innerInset * 2, this.size.height - innerInset * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        if (center != null) center()
    }
}

/** Kompakte Prozentanzeige für die Mitte eines [LifeProgress]. */
@Composable
fun ProgressCenter(value: String, caption: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Continuum.type.figuresMedium)
        if (caption != null) Text(caption.uppercase(), style = Continuum.type.label)
    }
}
