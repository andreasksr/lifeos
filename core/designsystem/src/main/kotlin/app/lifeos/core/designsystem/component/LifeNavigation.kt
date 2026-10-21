package app.lifeos.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.designsystem.util.rememberLifeHaptics

data class LifeNavItem(val key: String, val label: String, val glyph: Glyph)

/**
 * LifeNavigation – die Navigation ist die Fortsetzung der LifeLine: Die vertikale Linie biegt unten
 * in diese horizontale Linie ein; jedes Ziel ist ein Punkt darauf. Ein Lichtsegment gleitet zum
 * gewählten Bereich. Es werden nur tatsächlich implementierte Ziele übergeben.
 */
@Composable
fun LifeNavigation(
    items: List<LifeNavItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    lineStartX: Dp = 0.dp,
) {
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }
    val indicator by animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = tween(Continuum.motion.standardMillis),
        label = "navIndicator",
    )
    val haptics = rememberLifeHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(ContinuumColors.Obsidian)
                val start = lineStartX.toPx()
                drawLine(
                    Brush.horizontalGradient(listOf(ContinuumColors.Violet.copy(alpha = 0.55f), ContinuumColors.Cyan.copy(alpha = 0.35f)), startX = start, endX = size.width),
                    Offset(start, 0f), Offset(size.width, 0f), strokeWidth = 1.5.dp.toPx(),
                )
                if (items.isNotEmpty() && selectedIndex >= 0) {
                    val slot = size.width / items.size
                    val cx = slot * (indicator + 0.5f)
                    val half = slot * 0.32f
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, ContinuumColors.Cyan, ContinuumColors.Violet, Color.Transparent),
                            startX = cx - half, endX = cx + half,
                        ),
                        start = Offset(cx - half, 0f), end = Offset(cx + half, 0f), strokeWidth = 3.dp.toPx(),
                    )
                }
            }
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(ContinuumSpacing.navHeight)
                .padding(vertical = ContinuumSpacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Column(
                    Modifier
                        .weight(1f)
                        .semantics { this.selected = selected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClickLabel = item.label,
                        ) {
                            haptics.tick()
                            onSelect(item.key)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selected) {
                            LifeNode(shape = NodeShape.DOT, tone = NodeTone.ACTIVE, active = true, size = 28.dp)
                        }
                        LifeGlyph(item.glyph, tint = if (selected) ContinuumColors.Frost else ContinuumColors.Faint, size = 22.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label.uppercase(),
                        style = Continuum.type.label.copy(fontSize = Continuum.type.label.fontSize * 0.92f),
                        color = if (selected) ContinuumColors.Frost else ContinuumColors.Faint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Zentrale Schnellerfassung: großer Rautenknoten über der Navigationslinie, mit einer feinen
 * Verbindung zur Linie – „die Linie erzeugt einen neuen Knoten". Rechts unten: einhändig erreichbar.
 */
@Composable
fun LifeCaptureButton(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "Schnellerfassung") {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(Continuum.motion.quickMillis), label = "capturePress")
    val haptics = rememberLifeHaptics()
    Box(
        modifier
            .size(64.dp)
            .scale(scale)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.confirm()
                onClick()
            }
            .drawBehind {
                val c = Offset(size.width / 2, size.height / 2)
                val d = size.minDimension * 0.42f
                val p = Path().apply {
                    moveTo(c.x, c.y - d); lineTo(c.x + d, c.y); lineTo(c.x, c.y + d); lineTo(c.x - d, c.y); close()
                }
                drawCircle(
                    Brush.radialGradient(listOf(ContinuumColors.Cyan.copy(alpha = 0.28f), Color.Transparent), center = c, radius = d * 1.7f),
                    radius = d * 1.7f, center = c,
                )
                drawPath(p, ContinuumColors.Elevated)
                drawPath(p, Brush.linearGradient(listOf(ContinuumColors.Cyan, ContinuumColors.Violet)), style = Stroke(2.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        LifeGlyph(Glyph.PLUS, tint = ContinuumColors.Frost, size = 22.dp)
    }
}
