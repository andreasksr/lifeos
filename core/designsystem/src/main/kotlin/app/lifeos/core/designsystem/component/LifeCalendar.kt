package app.lifeos.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Monatsraster (Montag zuerst). Tage mit Einträgen tragen kleine Markierungen ([markers] = Anzahl je Tag),
 * heute ist mit einem Ring markiert, die Auswahl leuchtet. Wird für Datumsauswahl und Kalender genutzt.
 */
@Composable
fun LifeCalendarMonth(
    month: YearMonth,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    markers: Map<LocalDate, Int> = emptyMap(),
) {
    val haptics = rememberLifeHaptics()
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - DayOfWeek.MONDAY.value)
    val cells = offset + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                Text(it.uppercase(), style = Continuum.type.label, color = ContinuumColors.Faint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNumber = r * 7 + c - offset + 1
                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNumber)
                        val isSelected = date == selected
                        val isToday = date == today
                        val count = markers[date] ?: 0
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .semantics {
                                    this.selected = isSelected
                                    contentDescription = buildString {
                                        append(LifeFormat.longDate(date))
                                        if (isToday) append(", heute")
                                        if (count > 0) append(", $count Einträge")
                                    }
                                }
                                .clickable(role = Role.Button) {
                                    haptics.tick()
                                    onSelect(date)
                                }
                                .drawBehind {
                                    val ctr = Offset(size.width / 2, size.height / 2)
                                    val rad = size.minDimension * 0.36f
                                    if (isSelected) {
                                        drawCircle(ContinuumColors.Cyan.copy(alpha = 0.18f), radius = rad, center = ctr)
                                        drawCircle(ContinuumColors.Cyan, radius = rad, center = ctr, style = Stroke(1.5.dp.toPx()))
                                    } else if (isToday) {
                                        drawCircle(ContinuumColors.HairlineStrong, radius = rad, center = ctr, style = Stroke(1.dp.toPx()))
                                    }
                                    if (count > 0) {
                                        val dots = count.coerceAtMost(3)
                                        val gap = 5.dp.toPx()
                                        val startX = ctr.x - (dots - 1) * gap / 2
                                        repeat(dots) { i ->
                                            drawCircle(ContinuumColors.Violet, radius = 1.6.dp.toPx(), center = Offset(startX + i * gap, size.height * 0.84f))
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                dayNumber.toString(),
                                style = Continuum.type.figures,
                                color = when {
                                    isSelected -> ContinuumColors.Frost
                                    date.dayOfWeek.value >= 6 -> ContinuumColors.Muted
                                    else -> ContinuumColors.Frost.copy(alpha = 0.88f)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Kopf mit Monatsname und Pfeilen. */
@Composable
fun LifeMonthStepper(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LifeIconButton(Glyph.CHEVRON_LEFT, "Vorheriger Monat", onPrevious)
        Text(LifeFormat.monthTitle(month), style = Continuum.type.title, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        LifeIconButton(Glyph.CHEVRON_RIGHT, "Nächster Monat", onNext)
    }
}

/** Datumsauswahl als LifeOverlay – ersetzt den Material-DatePicker durch das CONTINUUM-Raster. */
@Composable
fun LifeDatePickerOverlay(
    visible: Boolean,
    initial: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
    title: String = "Datum wählen",
) {
    var month by remember(initial, visible) { mutableStateOf(YearMonth.from(initial)) }
    var picked by remember(initial, visible) { mutableStateOf(initial) }
    LifeOverlay(visible = visible, onDismiss = onDismiss, title = title, overline = LifeFormat.longDate(picked)) {
        LifeMonthStepper(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
        LifeCalendarMonth(month = month, selected = picked, today = today, onSelect = { picked = it })
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifeButton("Heute", { picked = today; month = YearMonth.from(today) }, style = LifeButtonStyle.SECONDARY)
            Box(Modifier.weight(1f))
            LifeButton("Übernehmen", { onPick(picked); onDismiss() })
        }
    }
}
