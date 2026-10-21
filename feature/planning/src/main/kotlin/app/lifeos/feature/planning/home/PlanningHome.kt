package app.lifeos.feature.planning.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifeos.core.designsystem.component.LifeEmpty
import app.lifeos.core.designsystem.component.LifeLink
import app.lifeos.core.designsystem.component.LifeNode
import app.lifeos.core.designsystem.component.LifeToastMessage
import app.lifeos.core.designsystem.component.LocalLifeSignal
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.designsystem.util.rememberLifeHaptics
import app.lifeos.core.domain.planning.AgendaBuilder
import app.lifeos.core.domain.planning.AgendaItem
import app.lifeos.core.domain.planning.TaskGrouping
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Task
import app.lifeos.feature.planning.PlanningDependencies
import app.lifeos.feature.planning.overview.EventLine
import app.lifeos.feature.planning.overview.PlanningEvent
import app.lifeos.feature.planning.overview.TaskLine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class PlanningHomeState(
    val today: LocalDate,
    /** Termine und fällige/überfällige Aufgaben von heute. */
    val todayItems: List<AgendaItem>,
    val upcoming: List<AgendaItem.EventItem>,
    val openCount: Int,
    val overdueCount: Int,
    val nextTasks: List<Task>,
    val projectNames: Map<NodeId, String>,
    val reminderTargets: Set<NodeId>,
)

/** Daten für die Startseite – die App bindet die Composables in Home ein. */
class PlanningHomeViewModel(private val deps: PlanningDependencies) : ViewModel() {
    private val ticker: Flow<LocalDate> = flow {
        while (true) {
            emit(deps.clock.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val _events = Channel<PlanningEvent>(Channel.BUFFERED)
    val events: Flow<PlanningEvent> = _events.receiveAsFlow()

    val state: StateFlow<PlanningHomeState?> = combine(
        deps.repository.observeTasks(),
        deps.repository.observeEvents(),
        deps.repository.observeReminders(),
        deps.projects.observeProjects(),
        ticker,
    ) { tasks, events, reminders, projects, today ->
        val now = LocalDateTime.ofInstant(deps.clock.now(), ZoneId.systemDefault())
        val groups = TaskGrouping.group(tasks, today)
        PlanningHomeState(
            today = today,
            todayItems = AgendaBuilder.build(today, today, today, tasks, events).firstOrNull()?.items.orEmpty(),
            upcoming = TaskGrouping.upcomingEvents(events, now, 7, 4).filter { it.occurrenceStart.toLocalDate() != today },
            openCount = groups.openCount,
            overdueCount = groups.overdue.size,
            nextTasks = (groups.upcoming + groups.someday).take(3),
            projectNames = projects.associate { it.id to it.name },
            reminderTargets = reminders.filter { it.enabled }.map { it.nodeId }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun complete(task: Task) {
        viewModelScope.launch {
            try {
                deps.completeTask(task.id)
                _events.send(PlanningEvent("„${task.title}“ erledigt", true))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(PlanningEvent("Speichern fehlgeschlagen.", false))
            }
        }
    }
}

data class PlanningHomeNavigation(
    val openTask: (NodeId) -> Unit,
    val openEvent: (NodeId) -> Unit,
    val openPlanning: () -> Unit,
    val newTask: () -> Unit,
)

@Composable
private fun PlanningSignals(vm: PlanningHomeViewModel) {
    val signal = LocalLifeSignal.current
    val haptics = rememberLifeHaptics()
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            if (e.ok) {
                haptics.confirm()
                signal.confirm(e.text)
            } else {
                signal.toast(LifeToastMessage(e.text, tone = NodeTone.ALERT))
            }
        }
    }
}

/**
 * Ergänzt den Home-Abschnitt „Heute" um die heutigen Termine und Aufgaben. Die Zeilen setzen ihre Knoten
 * direkt auf die LifeLine; der Knoten einer Aufgabe hakt sie ab. Erwartet einen Abschnitt in voller Breite.
 */
@Composable
fun PlanningTodayBlock(vm: PlanningHomeViewModel, nav: PlanningHomeNavigation, maxItems: Int) {
    PlanningSignals(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val inset = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)
    if (s.todayItems.isEmpty()) {
        Row(inset.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Heute keine Termine oder fälligen Aufgaben.", style = Continuum.type.bodyMuted, modifier = Modifier.weight(1f))
            LifeLink("Aufgabe", nav.newTask)
        }
        return
    }
    s.todayItems.take(maxItems).forEach { item ->
        when (item) {
            is AgendaItem.EventItem -> EventLine(item, s.today, item.event.id in s.reminderTargets) { nav.openEvent(item.event.id) }
            is AgendaItem.TaskItem -> TaskLine(
                task = item.task, today = s.today,
                projectName = item.task.projectId?.let { s.projectNames[it] },
                hasReminder = item.task.id in s.reminderTargets,
                onToggle = { vm.complete(item.task) },
                onOpen = { nav.openTask(item.task.id) },
                showDue = item.overdue,
            )
        }
    }
    if (s.todayItems.size > maxItems) {
        Text("+ ${s.todayItems.size - maxItems} weitere heute", style = Continuum.type.small, modifier = inset)
    }
}

/** Eigener Home-Abschnitt „Planung": nächste Termine und offene Aufgaben. */
@Composable
fun PlanningHomeSection(vm: PlanningHomeViewModel, nav: PlanningHomeNavigation, index: Int, advanced: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    SpineSection(
        "Planung", index = index, shape = NodeShape.DOT,
        tone = if (s.overdueCount > 0) NodeTone.ALERT else NodeTone.ACTIVE,
        onOpen = nav.openPlanning, openLabel = "Planung öffnen",
        trailing = { LifeLink("Öffnen", nav.openPlanning) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("OFFEN", style = Continuum.type.label)
                Text(s.openCount.toString(), style = Continuum.type.figuresLarge)
            }
            Column {
                Text("ÜBERFÄLLIG", style = Continuum.type.label, color = if (s.overdueCount > 0) ContinuumColors.Alert else ContinuumColors.Muted)
                Text(s.overdueCount.toString(), style = Continuum.type.figuresLarge, color = if (s.overdueCount > 0) ContinuumColors.Alert else ContinuumColors.Frost)
            }
        }
        if (s.upcoming.isEmpty() && s.nextTasks.isEmpty()) {
            LifeEmpty("In den nächsten 7 Tagen stehen keine Termine an.", actionLabel = "Termin oder Aufgabe planen", onAction = nav.openPlanning)
        }
        if (s.upcoming.isNotEmpty()) {
            Text("NÄCHSTE TERMINE", style = Continuum.type.label)
            s.upcoming.take(if (advanced) 4 else 2).forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LifeNode(shape = NodeShape.DOT, tone = NodeTone.ACTIVE, size = 10.dp, onClick = { nav.openEvent(e.event.id) }, onClickLabel = "Termin öffnen")
                    Text(e.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        LifeFormat.dayTitle(e.occurrenceStart.toLocalDate(), s.today) + if (e.event.allDay) "" else " · " + LifeFormat.time(e.occurrenceStart.toLocalTime()),
                        style = Continuum.type.figuresSmall, color = ContinuumColors.Muted,
                    )
                }
            }
        }
        if (advanced && s.nextTasks.isNotEmpty()) {
            Text("ALS NÄCHSTES", style = Continuum.type.label)
            s.nextTasks.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LifeNode(shape = NodeShape.RING, size = 10.dp, onClick = { nav.openTask(t.id) }, onClickLabel = "Aufgabe öffnen")
                    Text(t.title, style = Continuum.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    t.dueDate?.let { Text(LifeFormat.dayTitle(it, s.today), style = Continuum.type.small) }
                }
            }
        }
    }
}
