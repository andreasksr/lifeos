package app.lifeos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing

/**
 * LifeTimeline – zeitlicher Verlauf direkt an der LifeLine des LifeFrame. Jede Zeile setzt ihren Knoten
 * exakt auf die durchgehende Linie; dadurch entsteht beim Scrollen eine einzige, ununterbrochene Zeitachse.
 */
@Composable
fun LifeTimelineRow(
    modifier: Modifier = Modifier,
    nodeShape: NodeShape = NodeShape.DIAMOND,
    nodeTone: NodeTone = NodeTone.NEUTRAL,
    nodeActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val click = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
    } else {
        Modifier
    }
    SpineRow(
        modifier = modifier.heightIn(min = 56.dp).then(click),
        node = { LifeNode(shape = nodeShape, tone = nodeTone, active = nodeActive, size = 12.dp) },
    ) {
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

/** Kopf eines Zeitabschnitts (z. B. ein Tag) auf der Linie. */
@Composable
fun LifeTimelineHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    highlighted: Boolean = false,
) {
    SpineRow(
        modifier = modifier.heightIn(min = 40.dp).semantics { heading() },
        node = { LifeNode(shape = NodeShape.RING, tone = if (highlighted) NodeTone.ACTIVE else NodeTone.NEUTRAL, active = highlighted, size = 12.dp) },
    ) {
        Text(
            title.uppercase(),
            style = Continuum.type.label,
            color = if (highlighted) ContinuumColors.Frost else ContinuumColors.Muted,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) Text(trailing, style = Continuum.type.figuresSmall, color = ContinuumColors.Muted)
    }
}
