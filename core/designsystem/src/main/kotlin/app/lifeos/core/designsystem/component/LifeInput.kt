package app.lifeos.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.designsystem.util.rememberLifeHaptics

/**
 * Eingabefeld: nur eine Grundlinie, die beim Fokus zur leuchtenden LifeLine wird.
 * [error] wird unter dem Feld angezeigt und für Screenreader gesetzt.
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
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    leading: Glyph? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val errorText = error
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val lineAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(Continuum.motion.standardMillis), label = "fieldLine")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotEmpty()) {
            Text(label.uppercase(), style = Continuum.type.label, color = if (errorText != null) ContinuumColors.Alert else ContinuumColors.Muted)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = ContinuumColors.Frost),
            cursorBrush = SolidColor(ContinuumColors.Cyan),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .semantics {
                    if (errorText != null) this.error(errorText)
                    if (label.isNotEmpty()) contentDescription = label
                }
                .drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(
                        if (errorText != null) ContinuumColors.Alert else ContinuumColors.HairlineStrong,
                        Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    )
                    if (lineAlpha > 0f) {
                        drawLine(ContinuumColors.Signature, Offset(0f, y), Offset(size.width * lineAlpha, y), strokeWidth = 1.5.dp.toPx())
                    }
                },
            decorationBox = { inner ->
                Row(Modifier.padding(vertical = ContinuumSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        LifeGlyph(leading, tint = ContinuumColors.Muted, size = 18.dp)
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = textStyle, color = ContinuumColors.Faint)
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
        if (errorText != null) {
            Text(errorText, style = Continuum.type.small, color = ContinuumColors.Alert)
        }
    }
}

/**
 * Eigene Zifferntastatur für Beträge: keine Systemtastatur, große Tasten (einhändig), Komma und
 * Löschen. Validierung erfolgt durch den Aufrufer (exaktes Parsen mit `MoneyText`).
 */
@Composable
fun LifeKeypad(
    onDigit: (Char) -> Unit,
    onComma: () -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf(',', '0', '⌫'))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                ',' -> onComma()
                                '⌫' -> onBackspace()
                                else -> onDigit(key)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: Char, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberLifeHaptics()
    val description = when (key) {
        '⌫' -> "Löschen"
        ',' -> "Komma"
        else -> key.toString()
    }
    Box(
        modifier
            .height(56.dp)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.tick()
                onClick()
            }
            .drawBehind {
                if (pressed) {
                    drawCircle(ContinuumColors.Cyan.copy(alpha = 0.14f), radius = size.minDimension * 0.55f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (key == '⌫') {
            LifeGlyph(Glyph.BACKSPACE, tint = ContinuumColors.Muted)
        } else {
            Text(key.toString(), style = Continuum.type.figuresMedium.copy(fontSize = Continuum.type.figuresMedium.fontSize * 1.15f), textAlign = TextAlign.Center)
        }
    }
}

