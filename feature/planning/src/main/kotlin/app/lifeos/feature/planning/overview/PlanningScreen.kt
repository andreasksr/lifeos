package app.lifeos.feature.planning.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeCalendarMonth
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeIconButton
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeMonthStepper
import app.lifeos.core.designsystem.component.LifeSegmented
import app.lifeos.core.designsystem.component.LifeTimelineHeader
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.domain.planning.AgendaDay
import app.lifeos.core.domain.planning.AgendaItem
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Task
import java.time.LocalDate
import java.time.temporal.IsoFields

/** Navigation aus der Planung heraus – die App verbindet sie mit den Editoren. */
data class PlanningNavigation(
    val openTask: (NodeId?, LocalDate?) -> Unit,
    val openEvent: (NodeId?, LocalDate?) -> Unit,
)

@Composable
fun PlanningRoute(vm: PlanningViewModel, nav: PlanningNavigation) {
    val state by vm.state.collectAsStateWithLifecycle()
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            if (e.ok) {
                haptics.confirm()
                signal.confirm(e.text, if (e.undo != null) "Rückgängig" else null, e.undo)
            } else {
                haptics.reject()
                signal.toast(LifeToastMessage(e.text, tone = NodeTone.ALERT))
            }
        }
    }
    val s = state ?: return
    var showDone by rememberSaveable { mutableStateOf(false) }
    val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)

    LazyColumn(Modifier.fillMaxSize().testTag("lifeos-screen"), contentPadding = PaddingValues(bottom = 150.dp)) {
        item(key = "top") {
            LifeTopBar(
                overline = "Planung",
                title = when (s.view) {
                    PlanningView.DAY -> LifeFormat.dayTitle(s.selected, s.today)
                    PlanningView.WEEK -> "KW ${s.selected.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
                    PlanningView.MONTH -> LifeFormat.monthTitle(s.month)
                    PlanningView.TASKS -> "Aufgaben"
                },
                subtitle = when (s.view) {
                    PlanningView.DAY -> LifeFormat.longDate(s.selected)
                    PlanningView.TASKS -> "${s.groups.openCount} offen"
                    else -> null
                },
                actions = {
                    LifeIconButton(Glyph.TASKS, "Neue Aufgabe", { nav.openTask(null, if (s.view == PlanningView.TASKS) null else s.selected) }, tint = ContinuumColors.Cyan)
                    LifeIconButton(Glyph.CALENDAR, "Neuer Termin", { nav.openEvent(null, s.selected) }, tint = ContinuumColors.Cyan)
                },
            )
        }
        item(key = "views") {
            LifeSegmented(
                listOf(PlanningView.DAY to "Tag", PlanningView.WEEK to "Woche", PlanningView.MONTH to "Monat", PlanningView.TASKS to "Aufgaben"),
                s.view,
                vm::setView,
                modifier = inset.padding(bottom = 8.dp),
            )
        }
        if (s.needsNotificationPermission) {
            item(key = "perm") {
                SpineSection("Benachrichtigungen", shape = NodeShape.SQUARE, tone = NodeTone.WARN) {
                    Text(
                        "Du hast Erinnerungen eingerichtet, aber LifeOS darf keine Benachrichtigungen anzeigen. Ohne Erlaubnis wird nicht erinnert.",
                        style = Continuum.type.bodyMuted,
                    )
                    LifeButton("Benachrichtigungen erlauben", vm::requestNotifications, glyph = Glyph.BELL, style = LifeButtonStyle.SECONDARY)
                }
            }
        }

        when (s.view) {
            PlanningView.DAY -> {
                item(key = "stepper") {
                    DayStepper(
                        label = LifeFormat.dayTitle(s.selected, s.today),
                        onPrev = { vm.shift(-1) }, onNext = { vm.shift(1) },
                        showToday = s.selected != s.today, onToday = vm::today,
                        prevLabel = "Vorheriger Tag", nextLabel = "Nächster Tag",
                    )
                }
                agenda(s.days.first(), s, vm, nav, header = false)
                if (s.selected == s.today && s.groups.someday.isNotEmpty()) {
                    item(key = "someday-h") { LifeTimelineHeader("Ohne Datum", trailing = s.groups.someday.size.toString()) }
                    items(s.groups.someday.take(5), key = { "sd-" + it.id.value }) { t -> TaskRow(t, s, vm, nav, showDue = false) }
                    if (s.groups.someday.size > 5) {
                        item(key = "someday-more") { LifeLink("Alle Aufgaben", { vm.setView(PlanningView.TASKS) }, modifier = inset) }
                    }
                }
            }
            PlanningView.WEEK -> {
                item(key = "wstepper") {
                    val start = s.days.first().date
                    DayStepper(
                        label = "${LifeFormat.numericDate(start).take(6)} – ${LifeFormat.numericDate(start.plusDays(6))}",
                        onPrev = { vm.shift(-7) }, onNext = { vm.shift(7) },
                        showToday = s.days.none { it.date == s.today }, onToday = vm::today,
                        prevLabel = "Vorherige Woche", nextLabel = "Nächste Woche",
                    )
                }
                s.days.forEach { day -> agenda(day, s, vm, nav, header = true) }
            }
            PlanningView.MONTH -> {
                item(key = "month") {
                    Column(inset) {
                        LifeMonthStepper(s.month, { vm.shiftMonth(-1) }, { vm.shiftMonth(1) })
                        LifeCalendarMonth(month = s.month, selected = s.selected, today = s.today, onSelect = vm::select, markers = s.markers)
                    }
                }
                agenda(s.days.first(), s, vm, nav, header = true)
            }
            PlanningView.TASKS -> {
                val g = s.groups
                if (g.openCount == 0) {
                    item(key = "none") {
                        SpineSection("Offen", shape = NodeShape.RING) {
                            LifeEmpty(
                                "Keine offenen Aufgaben. Neue Aufgaben erscheinen hier – mit Fälligkeit, Priorität und Erinnerung.",
                                actionLabel = "Aufgabe anlegen",
                                onAction = { nav.openTask(null, null) },
                                glyph = Glyph.TASKS,
                            )
                        }
                    }
                }
                group("Überfällig", g.overdue, s, vm, nav, highlighted = false, showDue = true)
                group("Heute", g.today, s, vm, nav, highlighted = true, showDue = false)
                group("Demnächst", g.upcoming, s, vm, nav, highlighted = false, showDue = true)
                group("Ohne Datum", g.someday, s, vm, nav, highlighted = false, showDue = false)
                if (g.done.isNotEmpty()) {
                    item(key = "done-toggle") {
                        Row(inset.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            LifeLink(if (showDone) "Erledigte ausblenden" else "Erledigte anzeigen (${g.done.size})", { showDone = !showDone })
                        }
                    }
                    if (showDone) group("Erledigt", g.done, s, vm, nav, highlighted = false, showDue = true)
                }
            }
        }
    }
}

private fun LazyListScope.group(
    title: String,
    tasks: List<Task>,
    s: PlanningUiState,
    vm: PlanningViewModel,
    nav: PlanningNavigation,
    highlighted: Boolean,
    showDue: Boolean,
) {
    if (tasks.isEmpty()) return
    item(key = "g-$title") { LifeTimelineHeader(title, trailing = tasks.size.toString(), highlighted = highlighted) }
    items(tasks, key = { "t-$title-" + it.id.value }) { t -> TaskRow(t, s, vm, nav, showDue) }
}

private fun LazyListScope.agenda(day: AgendaDay, s: PlanningUiState, vm: PlanningViewModel, nav: PlanningNavigation, header: Boolean) {
    if (header) {
        item(key = "h-${day.date}") {
            LifeTimelineHeader(
                LifeFormat.dayTitle(day.date, s.today),
                trailing = if (day.items.isEmpty()) null else day.items.size.toString(),
                highlighted = day.date == s.today,
            )
        }
    }
    if (day.items.isEmpty()) {
        item(key = "e-${day.date}") {
            if (header) {
                Text(
                    "—", style = Continuum.type.small, color = ContinuumColors.Faint,
                    modifier = Modifier.padding(start = ContinuumSpacing.spineRail, bottom = 4.dp),
                )
            } else {
                Column(Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)) {
                    LifeEmpty(
                        if (day.date == s.today) "Heute stehen keine Termine oder fälligen Aufgaben an." else "Keine Termine oder fälligen Aufgaben.",
                        actionLabel = "Aufgabe für diesen Tag",
                        onAction = { nav.openTask(null, day.date) },
                        glyph = Glyph.CALENDAR,
                    )
                }
            }
        }
        return
    }
    items(day.items, key = { "a-${day.date}-" + it.id.value + (it as? AgendaItem.EventItem)?.occurrenceStart }) { item ->
        when (item) {
            is AgendaItem.EventItem -> EventLine(item, s.today, item.event.id in s.reminderTargets) { nav.openEvent(item.event.id, null) }
            is AgendaItem.TaskItem -> TaskRow(item.task, s, vm, nav, showDue = item.overdue)
        }
    }
}

@Composable
private fun TaskRow(t: Task, s: PlanningUiState, vm: PlanningViewModel, nav: PlanningNavigation, showDue: Boolean) {
    TaskLine(
        task = t,
        today = s.today,
        projectName = t.projectId?.let { s.projectNames[it] },
        hasReminder = t.id in s.reminderTargets,
        onToggle = { vm.toggle(t) },
        onOpen = { nav.openTask(t.id, null) },
        showDue = showDue,
    )
}

@Composable
private fun DayStepper(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    showToday: Boolean,
    onToday: () -> Unit,
    prevLabel: String,
    nextLabel: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = ContinuumSpacing.spineRail - 8.dp, end = ContinuumSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LifeIconButton(Glyph.CHEVRON_LEFT, prevLabel, onPrev)
        Text(label, style = Continuum.type.title, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        if (showToday) LifeLink("Heute", onToday)
        LifeIconButton(Glyph.CHEVRON_RIGHT, nextLabel, onNext)
    }
}
