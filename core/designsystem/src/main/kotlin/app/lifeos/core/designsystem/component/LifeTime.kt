package app.lifeos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.model.LifeFormat
import java.time.LocalTime

/**
 * Uhrzeitauswahl als LifeOverlay: Stunden als 6×4-Raster, Minuten in 5er-Schritten.
 * Zwei Fingertipps statt Scrollrädern – einhändig und ohne Material-TimePicker.
 */
@Composable
fun LifeTimePickerOverlay(
    visible: Boolean,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
    title: String = "Uhrzeit wählen",
) {
    var hour by remember(initial, visible) { mutableStateOf(initial.hour) }
    var minute by remember(initial, visible) { mutableStateOf((initial.minute / 5) * 5) }
    val haptics = rememberLifeHaptics()
    LifeOverlay(visible = visible, onDismiss = onDismiss, title = title, overline = LifeFormat.time(LocalTime.of(hour, minute))) {
        Text("STUNDE", style = Continuum.type.label)
        TimeGrid((0..23).toList(), columns = 6, selected = hour, label = { it.toString().padStart(2, '0') }) {
            haptics.tick()
            hour = it
        }
        Text("MINUTE", style = Continuum.type.label)
        TimeGrid((0..55 step 5).toList(), columns = 6, selected = minute, label = { ":" + it.toString().padStart(2, '0') }) {
            haptics.tick()
            minute = it
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Abbrechen", onDismiss, style = LifeButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            LifeButton("Übernehmen", { onPick(LocalTime.of(hour, minute)); onDismiss() }, glyph = Glyph.CHECK)
        }
    }
}

@Composable
private fun TimeGrid(values: List<Int>, columns: Int, selected: Int, label: (Int) -> String, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { v ->
                    val isSelected = v == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .semantics {
                                this.selected = isSelected
                                contentDescription = label(v)
                            }
                            .clickable(role = Role.RadioButton) { onSelect(v) }
                            .drawBehind {
                                if (isSelected) {
                                    drawRect(ContinuumColors.Cyan.copy(alpha = 0.14f))
                                    drawLine(ContinuumColors.Cyan, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
                                } else {
                                    drawLine(ContinuumColors.Hairline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label(v),
                            style = Continuum.type.figures,
                            color = if (isSelected) ContinuumColors.Frost else ContinuumColors.Muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Kleiner Zähler „− n +" (z. B. „alle 2 Wochen"). */
@Composable
fun LifeStepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LifeIconButton(Glyph.CHEVRON_LEFT, "$label verringern", { if (value > range.first) onChange(value - 1) }, enabled = value > range.first)
        Text(
            format(value),
            style = Continuum.type.figures,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp).semantics { contentDescription = "$label: ${format(value)}" },
        )
        LifeIconButton(Glyph.CHEVRON_RIGHT, "$label erhöhen", { if (value < range.last) onChange(value + 1) }, enabled = value < range.last)
    }
}
