package app.lifeos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing

/** Position eines Elements auf der vertikalen Zeitachse – bestimmt, welche Spine-Abschnitte sichtbar sind. */
enum class SpineSegment { FIRST, MIDDLE, LAST, SINGLE }

fun spineSegment(index: Int, count: Int): SpineSegment = when {
    count <= 1 -> SpineSegment.SINGLE
    index == 0 -> SpineSegment.FIRST
    index == count - 1 -> SpineSegment.LAST
    else -> SpineSegment.MIDDLE
}

/**
 * LifeTimeline – Baustein für vertikale Zeitachsen in `LazyColumn`s.
 *
 * Jede Zeile zeichnet ihren Abschnitt der durchgehenden Lichtlinie in der linken Schiene
 * ([ContinuumSpacing.timelineRail]) und setzt einen [LifeNode] auf Höhe des Inhalts.
 * So entsteht über alle Zeilen hinweg eine einzige, ununterbrochene Linie – auch beim Scrollen
 * und ohne dass die Liste als Ganzes gemessen werden muss.
 */
@Composable
fun LifeTimelineRow(
    segment: SpineSegment,
    modifier: Modifier = Modifier,
    nodeShape: NodeShape = NodeShape.DIAMOND,
    nodeTone: NodeTone = NodeTone.NEUTRAL,
    nodeActive: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ContinuumSpacing.touchTarget)
            .drawBehind {
                val x = ContinuumSpacing.timelineRail.toPx() / 2f
                val mid = size.height / 2f
                val stroke = ContinuumSpacing.line.toPx()
                val brush = Brush.verticalGradient(
                    listOf(ContinuumColors.Cyan.copy(alpha = 0.55f), ContinuumColors.Violet.copy(alpha = 0.55f)),
                )
                val top = if (segment == SpineSegment.FIRST || segment == SpineSegment.SINGLE) mid else 0f
                val bottom = if (segment == SpineSegment.LAST || segment == SpineSegment.SINGLE) mid else size.height
                if (bottom > top) {
                    drawLine(brush, Offset(x, top), Offset(x, bottom), strokeWidth = stroke)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ContinuumSpacing.timelineRail), contentAlignment = Alignment.Center) {
            LifeNode(shape = nodeShape, tone = nodeTone, active = nodeActive, size = 12.dp)
        }
        Row(
            Modifier
                .weight(1f)
                .padding(vertical = ContinuumSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.m),
            content = content,
        )
    }
}

/**
 * Kopf eines Zeitabschnitts (z. B. ein Tag). Die Linie läuft durch den Kopf hindurch,
 * der Ring-Knoten markiert den Abschnitt.
 */
@Composable
fun LifeTimelineHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    continuesAbove: Boolean = true,
    continuesBelow: Boolean = true,
    highlighted: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .drawBehind {
                val x = ContinuumSpacing.timelineRail.toPx() / 2f
                val mid = size.height / 2f
                val stroke = ContinuumSpacing.line.toPx()
                val color = ContinuumColors.Violet.copy(alpha = 0.4f)
                if (continuesAbove) drawLine(color, Offset(x, 0f), Offset(x, mid), strokeWidth = stroke)
                if (continuesBelow) drawLine(color, Offset(x, mid), Offset(x, size.height), strokeWidth = stroke)
            }
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ContinuumSpacing.timelineRail), contentAlignment = Alignment.Center) {
            LifeNode(shape = NodeShape.RING, tone = if (highlighted) NodeTone.INFLOW else NodeTone.NEUTRAL, active = highlighted, size = 14.dp)
        }
        Text(title.uppercase(), style = Continuum.type.label, color = if (highlighted) ContinuumColors.Ice else ContinuumColors.IceMuted, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = Continuum.type.figures.copy(fontSize = Continuum.type.label.fontSize * 1.1f), color = ContinuumColors.IceMuted)
        }
    }
}
