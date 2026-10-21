package app.lifeos.feature.planning.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.planning.AgendaBuilder
import app.lifeos.core.domain.planning.AgendaDay
import app.lifeos.core.domain.planning.PlanningResult
import app.lifeos.core.domain.planning.TaskGrouping
import app.lifeos.core.domain.planning.TaskGroups
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Reminder
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import app.lifeos.feature.planning.PlanningDependencies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class PlanningView { DAY, WEEK, MONTH, TASKS }

data class PlanningUiState(
    val view: PlanningView,
    val today: LocalDate,
    val selected: LocalDate,
    val month: YearMonth,
    /** Agenda für den angezeigten Zeitraum (Tag, Woche oder gewählter Tag im Monat). */
    val days: List<AgendaDay>,
    val groups: TaskGroups,
    val markers: Map<LocalDate, Int>,
    val projectNames: Map<NodeId, String>,
    val reminderTargets: Set<NodeId>,
    val notificationsAllowed: Boolean,
) {
    val needsNotificationPermission: Boolean get() = reminderTargets.isNotEmpty() && !notificationsAllowed
}

/** Nachricht an die Oberfläche: Text, Erfolg?, optional Rückgängig. */
data class PlanningEvent(val text: String, val ok: Boolean, val undo: (() -> Unit)? = null)

class PlanningViewModel(private val deps: PlanningDependencies) : ViewModel() {

    private val ticker: Flow<LocalDate> = flow {
        while (true) {
            emit(deps.clock.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val view = MutableStateFlow(PlanningView.DAY)
    private val selected = MutableStateFlow(deps.clock.today())
    private val month = MutableStateFlow(YearMonth.from(deps.clock.today()))

    private val _events = Channel<PlanningEvent>(Channel.BUFFERED)
    val events: Flow<PlanningEvent> = _events.receiveAsFlow()

    private data class Selection(val view: PlanningView, val selected: LocalDate, val month: YearMonth)

    private val selection = combine(view, selected, month) { v, s, m -> Selection(v, s, m) }

    private data class Data(val tasks: List<Task>, val events: List<app.lifeos.core.model.CalendarEvent>, val reminders: List<Reminder>, val projects: Map<NodeId, String>)

    private val data = combine(
        deps.repository.observeTasks(),
        deps.repository.observeEvents(),
        deps.repository.observeReminders(),
        deps.projects.observeProjects(),
    ) { t, e, r, p -> Data(t, e, r, p.associate { it.id to it.name }) }

    val state: StateFlow<PlanningUiState?> = combine(data, selection, ticker, deps.notifications.allowed) { d, sel, today, allowed ->
        val (from, to) = when (sel.view) {
            PlanningView.DAY, PlanningView.MONTH -> sel.selected to sel.selected
            PlanningView.WEEK -> TaskGrouping.weekStart(sel.selected).let { it to it.plusDays(6) }
            PlanningView.TASKS -> today to today
        }
        val agenda = AgendaBuilder.build(from, to, today, d.tasks, d.events, includeDone = true)
        // Im Wochen-/Tagesmodus jeden Tag zeigen – auch leere, damit die Linie die Zeit trägt.
        val days = if (sel.view == PlanningView.WEEK) {
            (0..6).map { i -> from.plusDays(i.toLong()) }.map { date -> agenda.firstOrNull { it.date == date } ?: AgendaDay(date, emptyList()) }
        } else {
            listOf(agenda.firstOrNull { it.date == from } ?: AgendaDay(from, emptyList()))
        }
        val monthStart = sel.month.atDay(1)
        val monthEnd = sel.month.atEndOfMonth()
        val markers = AgendaBuilder.build(monthStart, monthEnd, today, d.tasks, d.events)
            .associate { it.date to it.items.size }
        PlanningUiState(
            view = sel.view,
            today = today,
            selected = sel.selected,
            month = sel.month,
            days = days,
            groups = TaskGrouping.group(d.tasks, today),
            markers = markers,
            projectNames = d.projects,
            reminderTargets = d.reminders.filter { it.enabled }.map { it.nodeId }.toSet(),
            notificationsAllowed = allowed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setView(v: PlanningView) {
        view.value = v
        if (v == PlanningView.MONTH) month.value = YearMonth.from(selected.value)
    }

    fun select(date: LocalDate) {
        selected.value = date
        month.value = YearMonth.from(date)
    }

    fun shift(days: Long) = select(selected.value.plusDays(days))
    fun today() = select(deps.clock.today())
    fun shiftMonth(months: Long) {
        month.value = month.value.plusMonths(months)
    }

    fun requestNotifications() = deps.notifications.request()

    /** Abhaken über den Knoten auf der Linie. Erledigte Aufgaben lassen sich wieder öffnen. */
    fun toggle(task: Task) {
        viewModelScope.launch {
            try {
                if (task.status == TaskStatus.DONE) {
                    deps.reopenTask(task.id)
                    _events.send(PlanningEvent("„${task.title}“ wieder offen", true))
                } else {
                    when (val r = deps.completeTask(task.id)) {
                        is PlanningResult.Success -> {
                            val next = r.value
                            val text = if (next.status == TaskStatus.OPEN && next.dueDate != null) {
                                "Erledigt – nächste Fälligkeit ${app.lifeos.core.model.LifeFormat.numericDate(next.dueDate!!)}"
                            } else {
                                "„${task.title}“ erledigt"
                            }
                            _events.send(PlanningEvent(text, true))
                        }
                        is PlanningResult.Invalid -> _events.send(PlanningEvent("Aufgabe nicht gefunden.", false))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(PlanningEvent("Speichern fehlgeschlagen. Es wurde nichts verändert.", false))
            }
        }
    }
}
