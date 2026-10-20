package app.lifeos.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors

/** Geometrie eines Knotens – trägt Bedeutung, nicht nur Farbe (Barrierefreiheit). */
enum class NodeShape {
    /** Raute: Ereignis mit Wert (Buchung). */
    DIAMOND,

    /** Ring: Struktur / Container (Tag, Konto, Projekt). */
    RING,

    /** Punkt: einfacher Zeitpunkt. */
    DOT,

    /** Doppelring: Verknüpfung zwischen Bereichen. */
    LINKED,
}

enum class NodeTone { NEUTRAL, INFLOW, OUTFLOW, ALERT }

/**
 * Ein Knoten auf der LifeLine: markiert einen Punkt in Zeit, Geldfluss oder Fortschritt.
 * [active] lässt den Knoten aufleuchten (z. B. ausgewähltes Navigationsziel, heutiger Tag).
 */
@Composable
fun LifeNode(
    modifier: Modifier = Modifier,
    shape: NodeShape = NodeShape.DOT,
    tone: NodeTone = NodeTone.NEUTRAL,
    active: Boolean = false,
    size: Dp = 12.dp,
) {
    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(Continuum.motion.standardMillis),
        label = "nodeGlow",
    )
    Canvas(modifier.size(size)) {
        drawLifeNode(shape, toneColor(tone), glow)
    }
}

internal fun toneColor(tone: NodeTone): Color = when (tone) {
    NodeTone.NEUTRAL -> ContinuumColors.Ice
    NodeTone.INFLOW -> ContinuumColors.Cyan
    NodeTone.OUTFLOW -> ContinuumColors.Violet
    NodeTone.ALERT -> ContinuumColors.Flare
}

internal fun DrawScope.drawLifeNode(shape: NodeShape, color: Color, glow: Float) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f
    val stroke = 1.5.dp.toPx()

    if (glow > 0f) {
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.45f * glow), Color.Transparent), center = c, radius = r * 2.2f),
            radius = r * 2.2f,
            center = c,
        )
    }
    when (shape) {
        NodeShape.DOT -> drawCircle(color, radius = r * 0.45f, center = c)
        NodeShape.RING -> {
            drawCircle(ContinuumColors.Obsidian, radius = r * 0.8f, center = c)
            drawCircle(color, radius = r * 0.8f - stroke / 2, center = c, style = Stroke(stroke))
            if (glow > 0f) drawCircle(color, radius = r * 0.3f * glow, center = c)
        }
        NodeShape.DIAMOND -> {
            val d = r * 0.85f
            val path = Path().apply {
                moveTo(c.x, c.y - d)
                lineTo(c.x + d, c.y)
                lineTo(c.x, c.y + d)
                lineTo(c.x - d, c.y)
                close()
            }
            drawPath(path, ContinuumColors.Obsidian)
            drawPath(path, color, style = Stroke(stroke))
            drawCircle(color, radius = r * 0.18f, center = c)
        }
        NodeShape.LINKED -> {
            drawCircle(ContinuumColors.Obsidian, radius = r * 0.9f, center = c)
            drawCircle(color, radius = r * 0.9f - stroke / 2, center = c, style = Stroke(stroke))
            drawCircle(color.copy(alpha = 0.7f), radius = r * 0.45f, center = c, style = Stroke(stroke))
        }
    }
}
