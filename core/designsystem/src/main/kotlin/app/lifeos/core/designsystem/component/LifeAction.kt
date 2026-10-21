package app.lifeos.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.designsystem.util.rememberLifeHaptics

enum class LifeButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER }

/**
 * LifeAction – die einheitliche Schaltfläche. PRIMARY: Lichtkontur mit Signaturverlauf und Füllung;
 * SECONDARY: Haarlinie; GHOST: nur Text; DANGER: Alert-Kontur. Abgeschrägte Ecken statt Rundungen.
 */
@Composable
fun LifeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LifeButtonStyle = LifeButtonStyle.PRIMARY,
    enabled: Boolean = true,
    glyph: Glyph? = null,
    fill: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(Continuum.motion.quickMillis), label = "btnAlpha")
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, tween(Continuum.motion.quickMillis), label = "btnPress")
    val haptics = rememberLifeHaptics()
    val textColor = when (style) {
        LifeButtonStyle.DANGER -> ContinuumColors.Alert
        LifeButtonStyle.PRIMARY -> ContinuumColors.Frost
        else -> ContinuumColors.Frost
    }
    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = ContinuumSpacing.touchTarget)
            .widthIn(min = 64.dp)
            .scale(press)
            .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null) {
                haptics.tick()
                onClick()
            }
            .drawBehind {
                val cut = 9.dp.toPx()
                val s = 1.dp.toPx()
                val path = Path().apply {
                    moveTo(cut, s / 2); lineTo(size.width - s / 2, s / 2)
                    lineTo(size.width - s / 2, size.height - cut); lineTo(size.width - cut, size.height - s / 2)
                    lineTo(s / 2, size.height - s / 2); lineTo(s / 2, cut); close()
                }
                when (style) {
                    LifeButtonStyle.PRIMARY -> {
                        drawPath(path, Brush.horizontalGradient(listOf(ContinuumColors.Cyan.copy(alpha = 0.16f), ContinuumColors.Violet.copy(alpha = 0.16f))), alpha = alpha)
                        drawPath(path, ContinuumColors.Signature, alpha = alpha, style = Stroke(1.5.dp.toPx()))
                        if (pressed) drawPath(path, ContinuumColors.Cyan.copy(alpha = 0.12f))
                    }
                    LifeButtonStyle.SECONDARY -> {
                        drawPath(path, ContinuumColors.Deep.copy(alpha = 0.8f * alpha))
                        drawPath(path, ContinuumColors.HairlineStrong, alpha = alpha, style = Stroke(s))
                    }
                    LifeButtonStyle.GHOST -> if (pressed) drawPath(path, ContinuumColors.Hairline)
                    LifeButtonStyle.DANGER -> drawPath(path, ContinuumColors.Alert, alpha = alpha * 0.8f, style = Stroke(s))
                }
            }
            .padding(horizontal = ContinuumSpacing.l, vertical = ContinuumSpacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                LifeGlyph(glyph, tint = textColor.copy(alpha = alpha), size = 18.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text.uppercase(),
                style = Continuum.type.label.copy(fontSize = Continuum.type.label.fontSize * 1.12f),
                color = textColor.copy(alpha = alpha),
            )
        }
    }
}

/** Runde 48-dp-Glyph-Schaltfläche für Kopfzeilen und Listen. */
@Composable
fun LifeIconButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ContinuumColors.Muted,
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    val haptics = rememberLifeHaptics()
    Box(
        modifier
            .size(ContinuumSpacing.touchTarget)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.tick()
                onClick()
            }
            .drawBehind {
                if (highlighted) {
                    drawCircle(ContinuumColors.Cyan.copy(alpha = 0.12f), radius = size.minDimension / 2.6f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        LifeGlyph(glyph, tint = if (enabled) (if (highlighted) ContinuumColors.Cyan else tint) else tint.copy(alpha = 0.3f))
    }
}

/** Textlink mit Pfeil – für „Alle anzeigen →". */
@Composable
fun LifeLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$text  →", style = Continuum.type.label, color = ContinuumColors.Cyan)
    }
}

/** Segmentauswahl – ein Licht-Unterstrich markiert die Wahl. */
@Composable
fun <T> LifeSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberLifeHaptics()
    Row(modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val color by animateColorAsState(
                if (isSelected) ContinuumColors.Frost else ContinuumColors.Faint,
                tween(Continuum.motion.quickMillis), label = "segColor",
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = ContinuumSpacing.touchTarget)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) {
                        haptics.tick()
                        onSelect(value)
                    }
                    .drawBehind {
                        val y = size.height - 1.dp.toPx()
                        drawLine(ContinuumColors.Hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        if (isSelected) drawLine(ContinuumColors.Signature, Offset(size.width * 0.12f, y), Offset(size.width * 0.88f, y), strokeWidth = 2.dp.toPx())
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = Continuum.type.label, color = color, maxLines = 1)
            }
        }
    }
}

/** Auswahl-Chip (Kategorie, Konto, Filter). */
@Composable
fun LifeChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    accent: Color = ContinuumColors.Cyan,
) {
    val haptics = rememberLifeHaptics()
    Box(
        modifier
            .heightIn(min = 40.dp)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton) {
                haptics.tick()
                onClick()
            }
            .drawBehind {
                val s = 1.dp.toPx()
                val cut = 6.dp.toPx()
                val path = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width - cut, 0f); lineTo(size.width, cut)
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(path, if (selected) accent.copy(alpha = 0.13f) else ContinuumColors.Deep)
                drawPath(path, if (selected) accent.copy(alpha = 0.8f) else ContinuumColors.Hairline, style = Stroke(s))
            }
            .padding(horizontal = ContinuumSpacing.m, vertical = ContinuumSpacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (glyph != null) LifeGlyph(glyph, tint = if (selected) accent else ContinuumColors.Muted, size = 16.dp)
            Text(
                text,
                style = Continuum.type.body.copy(fontSize = Continuum.type.body.fontSize * 0.93f),
                color = if (selected) ContinuumColors.Frost else ContinuumColors.Muted,
                maxLines = 1,
            )
        }
    }
}

/** Schalter als kurze Linie mit gleitendem Knoten. */
@Composable
fun LifeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(Continuum.motion.standardMillis), label = "toggle")
    val haptics = rememberLifeHaptics()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ContinuumSpacing.touchTarget)
            .semantics { stateDescription = if (checked) "an" else "aus" }
            .clickable(role = Role.Switch) {
                haptics.tick()
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = Continuum.type.body)
            if (description != null) Text(description, style = Continuum.type.small)
        }
        androidx.compose.foundation.Canvas(Modifier.size(width = 44.dp, height = 24.dp)) {
            val y = size.height / 2
            val r = 7.dp.toPx()
            drawLine(ContinuumColors.HairlineStrong, Offset(r, y), Offset(size.width - r, y), strokeWidth = 2.dp.toPx())
            if (position > 0f) drawLine(ContinuumColors.Signature, Offset(r, y), Offset(r + (size.width - 2 * r) * position, y), strokeWidth = 2.dp.toPx())
            val cx = r + (size.width - 2 * r) * position
            drawCircle(ContinuumColors.Obsidian, radius = r, center = Offset(cx, y))
            drawCircle(if (checked) ContinuumColors.Cyan else ContinuumColors.Muted, radius = r, center = Offset(cx, y), style = Stroke(1.5.dp.toPx()))
            if (checked) drawCircle(ContinuumColors.Cyan, radius = r * 0.4f, center = Offset(cx, y))
        }
    }
}
