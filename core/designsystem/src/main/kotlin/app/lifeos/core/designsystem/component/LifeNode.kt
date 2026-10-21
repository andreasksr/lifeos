package app.lifeos.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.util.rememberLifeHaptics

/** Geometrie eines Knotens – trägt Bedeutung, nicht nur Farbe (Barrierefreiheit). */
enum class NodeShape {
    /** Raute: Ereignis mit Wert (Buchung, Kosten). */
    DIAMOND,

    /** Ring: Struktur / Abschnitt (Tag, Konto, Bereich). */
    RING,

    /** Punkt: einfacher Zeitpunkt / Aufgabe. */
    DOT,

    /** Doppelring: Verknüpfung zwischen Bereichen / Projekt. */
    LINKED,

    /** Sechseck: Besitz / „Mein Leben". */
    HEX,

    /** Quadrat: Meilenstein / Prüfung. */
    SQUARE,
}

enum class NodeTone {
    NEUTRAL,

    /** Zufluss / positiv – Mint. */
    INFLOW,

    /** Abfluss – Violett. */
    OUTFLOW,

    /** Aktiv / jetzt – Cyan. */
    ACTIVE,

    /** Bald fällig – Amber. */
    WARN,

    /** Überschritten / überfällig – Alert. */
    ALERT,

    /** Erledigt – Mint, gefüllt. */
    DONE,
}

fun toneColor(tone: NodeTone): Color = when (tone) {
    NodeTone.NEUTRAL -> ContinuumColors.Frost
    NodeTone.INFLOW, NodeTone.DONE -> ContinuumColors.Mint
    NodeTone.OUTFLOW -> ContinuumColors.Violet
    NodeTone.ACTIVE -> ContinuumColors.Cyan
    NodeTone.WARN -> ContinuumColors.Amber
    NodeTone.ALERT -> ContinuumColors.Alert
}

/**
 * Ein Knoten auf der LifeLine. Ist [onClick] gesetzt, ist der Knoten interaktiv: 48-dp-Touchfläche,
 * Leuchten beim Drücken, haptische Bestätigung – er öffnet die zugehörigen Details.
 */
@Composable
fun LifeNode(
    modifier: Modifier = Modifier,
    shape: NodeShape = NodeShape.DOT,
    tone: NodeTone = NodeTone.NEUTRAL,
    active: Boolean = false,
    size: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glow by animateFloatAsState(
        targetValue = if (active || pressed) 1f else 0f,
        animationSpec = tween(Continuum.motion.standardMillis),
        label = "nodeGlow",
    )
    val haptics = rememberLifeHaptics()
    val clickModifier = if (onClick != null) {
        Modifier
            .size(maxOf(size, 48.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
            ) {
                haptics.tick()
                onClick()
            }
    } else {
        Modifier
    }
    Box(modifier.then(clickModifier), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) { drawLifeNode(shape, toneColor(tone), glow, filled = tone == NodeTone.DONE) }
    }
}

fun DrawScope.drawLifeNode(shape: NodeShape, color: Color, glow: Float, filled: Boolean = false) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f
    val stroke = 1.5.dp.toPx()
    val core = ContinuumColors.Obsidian

    if (glow > 0f) {
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.5f * glow), Color.Transparent), center = c, radius = r * 2.6f),
            radius = r * 2.6f, center = c,
        )
    }
    fun polygon(points: List<Offset>): Path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    when (shape) {
        NodeShape.DOT -> drawCircle(color, radius = r * 0.42f, center = c)
        NodeShape.RING -> {
            drawCircle(core, radius = r * 0.8f, center = c)
            drawCircle(color, radius = r * 0.8f - stroke / 2, center = c, style = Stroke(stroke))
            if (filled || glow > 0f) drawCircle(color, radius = r * 0.32f * maxOf(glow, if (filled) 1f else 0f), center = c)
        }
        NodeShape.DIAMOND -> {
            val d = r * 0.86f
            val p = polygon(listOf(Offset(c.x, c.y - d), Offset(c.x + d, c.y), Offset(c.x, c.y + d), Offset(c.x - d, c.y)))
            drawPath(p, if (filled) color else core)
            drawPath(p, color, style = Stroke(stroke))
            if (!filled) drawCircle(color, radius = r * 0.17f, center = c)
        }
        NodeShape.LINKED -> {
            drawCircle(core, radius = r * 0.9f, center = c)
            drawCircle(color, radius = r * 0.9f - stroke / 2, center = c, style = Stroke(stroke))
            drawCircle(color.copy(alpha = 0.75f), radius = r * 0.45f, center = c, style = Stroke(stroke))
        }
        NodeShape.HEX -> {
            val d = r * 0.88f
            val pts = (0 until 6).map { i ->
                val a = Math.toRadians((60.0 * i) - 90.0)
                Offset(c.x + (d * kotlin.math.cos(a)).toFloat(), c.y + (d * kotlin.math.sin(a)).toFloat())
            }
            val p = polygon(pts)
            drawPath(p, if (filled) color else core)
            drawPath(p, color, style = Stroke(stroke))
        }
        NodeShape.SQUARE -> {
            val d = r * 0.68f
            val p = polygon(listOf(Offset(c.x - d, c.y - d), Offset(c.x + d, c.y - d), Offset(c.x + d, c.y + d), Offset(c.x - d, c.y + d)))
            drawPath(p, if (filled) color else core)
            drawPath(p, color, style = Stroke(stroke))
        }
    }
}
