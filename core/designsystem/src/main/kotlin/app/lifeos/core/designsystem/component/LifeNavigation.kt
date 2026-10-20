package app.lifeos.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing

data class LifeNavItem(val key: String, val label: String, val shape: NodeShape)

/**
 * LifeNavigation – die Navigation ist selbst ein Abschnitt der LifeLine: eine horizontale Linie,
 * auf der jedes Ziel ein Knoten ist. Ein Lichtpunkt gleitet zum gewählten Ziel.
 * Es werden nur tatsächlich implementierte Ziele übergeben (keine funktionslosen Einträge).
 */
@Composable
fun LifeNavigation(
    items: List<LifeNavItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val motion = Continuum.motion
    val indicator by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(motion.standardMillis),
        label = "navIndicator",
    )
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(ContinuumColors.Obsidian) }
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .drawBehind {
                    drawLine(ContinuumColors.Hairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = size.height)
                    if (items.isNotEmpty()) {
                        val slot = size.width / items.size
                        val cx = slot * (indicator + 0.5f)
                        val half = slot * 0.35f
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, ContinuumColors.Cyan, ContinuumColors.Violet, Color.Transparent),
                                startX = cx - half,
                                endX = cx + half,
                            ),
                            start = Offset(cx - half, 0f),
                            end = Offset(cx + half, 0f),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = ContinuumSpacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .semantics { this.selected = selected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClickLabel = item.label,
                        ) { onSelect(item.key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LifeNode(
                        shape = item.shape,
                        tone = if (selected) NodeTone.INFLOW else NodeTone.NEUTRAL,
                        active = selected,
                        size = 14.dp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.label.uppercase(),
                        style = Continuum.type.label,
                        color = if (selected) ContinuumColors.Ice else ContinuumColors.IceFaint,
                    )
                }
            }
        }
    }
}
