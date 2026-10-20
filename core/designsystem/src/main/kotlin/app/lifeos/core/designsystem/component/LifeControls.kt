package app.lifeos.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing

enum class LifeButtonStyle { PRIMARY, GHOST, DANGER }

/**
 * Schaltfläche: Kontur mit abgeschrägten Ecken; PRIMARY trägt die Cyan-Violett-Signatur.
 * Deaktivierte Schaltflächen werden nur verwendet, wenn der Grund sichtbar ist (z. B. Formularfehler).
 */
@Composable
fun LifeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LifeButtonStyle = LifeButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(Continuum.motion.quickMillis), label = "btnAlpha")
    Box(
        modifier
            .heightIn(min = ContinuumSpacing.touchTarget)
            .widthIn(min = 96.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                val cut = 8.dp.toPx()
                val s = 1.dp.toPx()
                val path = Path().apply {
                    moveTo(cut, s / 2); lineTo(size.width - s / 2, s / 2)
                    lineTo(size.width - s / 2, size.height - cut); lineTo(size.width - cut, size.height - s / 2)
                    lineTo(s / 2, size.height - s / 2); lineTo(s / 2, cut); close()
                }
                when (style) {
                    LifeButtonStyle.PRIMARY -> {
                        drawPath(path, ContinuumColors.Cyan.copy(alpha = 0.08f * alpha))
                        drawPath(path, ContinuumColors.Signature, alpha = alpha, style = Stroke(1.5.dp.toPx()))
                    }
                    LifeButtonStyle.GHOST -> drawPath(path, ContinuumColors.HairlineStrong, alpha = alpha, style = Stroke(s))
                    LifeButtonStyle.DANGER -> drawPath(path, ContinuumColors.Flare, alpha = alpha * 0.8f, style = Stroke(s))
                }
            }
            .padding(horizontal = ContinuumSpacing.l, vertical = ContinuumSpacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = Continuum.type.label.copy(fontSize = Continuum.type.label.fontSize * 1.1f),
            color = when (style) {
                LifeButtonStyle.DANGER -> ContinuumColors.Flare
                else -> ContinuumColors.Ice
            }.copy(alpha = alpha),
        )
    }
}

/**
 * Eingabefeld: nur eine Grundlinie, die beim Fokus zur leuchtenden LifeLine wird.
 * [error] wird als Text unter dem Feld angezeigt und für Screenreader gesetzt.
 */
@Composable
fun LifeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    textStyle: TextStyle = Continuum.type.body,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    val errorText = error
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val lineAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(Continuum.motion.standardMillis), label = "fieldLine")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), style = Continuum.type.label, color = if (error != null) ContinuumColors.Flare else ContinuumColors.IceMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = ContinuumColors.Ice),
            cursorBrush = SolidColor(ContinuumColors.Cyan),
            keyboardOptions = keyboardOptions,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .semantics { if (errorText != null) this.error(errorText) }
                .drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(
                        if (error != null) ContinuumColors.Flare else ContinuumColors.HairlineStrong,
                        Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    )
                    if (lineAlpha > 0f) {
                        drawLine(ContinuumColors.Signature, Offset(0f, y), Offset(size.width * lineAlpha, y), strokeWidth = 1.5.dp.toPx())
                    }
                },
            decorationBox = { inner ->
                Box(Modifier.padding(vertical = ContinuumSpacing.s), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = ContinuumColors.IceFaint)
                    }
                    inner()
                }
            },
        )
        if (error != null) {
            Text(error, style = Continuum.type.bodyMuted.copy(fontSize = Continuum.type.label.fontSize * 1.1f), color = ContinuumColors.Flare)
        }
    }
}

/** Segmentauswahl (z. B. Ausgabe / Einnahme / Umbuchung) – ein Licht-Unterstrich wandert mit. */
@Composable
fun <T> LifeSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val color by animateColorAsState(
                if (isSelected) ContinuumColors.Ice else ContinuumColors.IceFaint,
                tween(Continuum.motion.quickMillis), label = "segColor",
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = ContinuumSpacing.touchTarget)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .drawBehind {
                        val y = size.height - 1.dp.toPx()
                        drawLine(ContinuumColors.Hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        if (isSelected) drawLine(ContinuumColors.Signature, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = Continuum.type.label, color = color)
            }
        }
    }
}

/** Auswahl-Chip (Kategorie, Konto): Kontur, ausgewählt mit Signaturlinie und hellem Text. */
@Composable
fun LifeChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .drawBehind {
                val s = 1.dp.toPx()
                drawRect(
                    color = if (selected) ContinuumColors.Cyan.copy(alpha = 0.10f) else ContinuumColors.ObsidianRaised,
                )
                drawRect(
                    color = if (selected) ContinuumColors.Cyan.copy(alpha = 0.7f) else ContinuumColors.Hairline,
                    topLeft = Offset(s / 2, s / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - s, size.height - s),
                    style = Stroke(s),
                )
            }
            .padding(horizontal = ContinuumSpacing.m, vertical = ContinuumSpacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Continuum.type.body.copy(fontSize = Continuum.type.body.fontSize * 0.93f), color = if (selected) ContinuumColors.Ice else ContinuumColors.IceMuted)
    }
}

/** Kopfzeile: Titel links (Display), optionale Aktionen rechts, optionale Zurück-Aktion. */
@Composable
fun LifeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = ContinuumSpacing.gutter, vertical = ContinuumSpacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    Modifier
                        .heightIn(min = ContinuumSpacing.touchTarget)
                        .widthIn(min = ContinuumSpacing.touchTarget)
                        .clickable(role = Role.Button, onClickLabel = "Zurück", onClick = onBack),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("←", style = Continuum.type.title, color = ContinuumColors.IceMuted)
                }
            }
            Column(Modifier.weight(1f)) {
                if (overline != null) Text(overline.uppercase(), style = Continuum.type.label)
                Text(title, style = Continuum.type.display.copy(fontSize = Continuum.type.display.fontSize * 0.82f), modifier = Modifier.semantics { heading() })
            }
            actions()
        }
    }
}
