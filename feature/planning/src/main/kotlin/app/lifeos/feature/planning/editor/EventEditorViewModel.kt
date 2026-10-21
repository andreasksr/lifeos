package app.lifeos.feature.planning.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.planning.EventDraft
import app.lifeos.core.domain.planning.PlanningError
import app.lifeos.core.domain.planning.PlanningResult
import app.lifeos.core.model.CalendarEvent
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Reminder
import app.lifeos.core.model.Repeat
import app.lifeos.core.model.RepeatUnit
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import app.lifeos.feature.planning.PlanningDependencies
import app.lifeos.feature.planning.presentation.PlanningText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class EventField { TITLE, TIME, GENERAL }

data class EventForm(
    val title: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val location: String = "",
    val note: String = "",
    val repeatUnit: RepeatUnit? = null,
    val repeatInterval: Int = 1,
    val reminderMinutes: Int? = null,
    val linkedTaskId: NodeId? = null,
) {
    val start: LocalDateTime get() = startDate.atTime(startTime)
    val end: LocalDateTime get() = endDate.atTime(endTime)
}

data class EventSnapshot(val event: CalendarEvent, val reminders: List<Reminder>)

sealed interface EventOutcome {
    data class Saved(val text: String) : EventOutcome
    data class Deleted(val snapshot: EventSnapshot) : EventOutcome
}

data class EventEditorState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val form: EventForm,
    val openTasks: List<Task> = emptyList(),
    val today: LocalDate,
    val errors: Map<EventField, String> = emptyMap(),
    val saving: Boolean = false,
    val confirmDelete: Boolean = false,
    val outcome: EventOutcome? = null,
)

class EventEditorViewModel(
    private val deps: PlanningDependencies,
    private val eventId: NodeId?,
    presetDate: LocalDate?,
) : ViewModel() {

    private val initial: EventForm = run {
        val today = deps.clock.today()
        val date = presetDate ?: today
        val nowHour = LocalDateTime.ofInstant(deps.clock.now(), java.time.ZoneId.systemDefault()).toLocalTime().withMinute(0).withSecond(0).withNano(0).plusHours(1)
        val start = if (date == today && nowHour.hour != 0) nowHour else LocalTime.of(9, 0)
        val end = if (start.hour >= 23) LocalTime.of(23, 59) else start.plusHours(1)
        EventForm(startDate = date, startTime = start, endDate = date, endTime = end)
    }

    private val _state = MutableStateFlow(EventEditorState(isEdit = eventId != null, form = initial, today = deps.clock.today()))
    val state: StateFlow<EventEditorState> = _state.asStateFlow()
    val notificationsAllowed: StateFlow<Boolean> = deps.notifications.allowed

    private var originalLinkedTask: NodeId? = null

    init {
        viewModelScope.launch {
            try {
                val tasks = deps.repository.tasks()
                val existing = eventId?.let { deps.repository.event(it) }
                val reminder = eventId?.let { id -> deps.repository.reminders().firstOrNull { it.nodeId == id && it.enabled } }
                val linkedTask = eventId?.let { id ->
                    deps.links.linksOf(id).filter { it.relation == LinkRelation.RELATES_TO }
                        .map { if (it.from == id) it.to else it.from }
                        .firstOrNull { tid -> tasks.any { it.id == tid } }
                }
                originalLinkedTask = linkedTask
                val form = existing?.let {
                    EventForm(
                        title = it.title,
                        allDay = it.allDay,
                        startDate = it.start.toLocalDate(),
                        startTime = it.start.toLocalTime(),
                        endDate = it.end.toLocalDate(),
                        endTime = it.end.toLocalTime(),
                        location = it.location.orEmpty(),
                        note = it.note.orEmpty(),
                        repeatUnit = it.repeat?.unit,
                        repeatInterval = it.repeat?.interval ?: 1,
                        reminderMinutes = reminder?.minutesBefore,
                        linkedTaskId = linkedTask,
                    )
                } ?: initial
                _state.update {
                    it.copy(
                        loading = false,
                        form = form,
                        openTasks = tasks.filter { t -> t.status == TaskStatus.OPEN || t.id == linkedTask },
                        errors = if (eventId != null && existing == null) mapOf(EventField.GENERAL to "Dieser Termin existiert nicht mehr.") else emptyMap(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, errors = mapOf(EventField.GENERAL to "Daten konnten nicht geladen werden.")) }
            }
        }
    }

    fun update(change: (EventForm) -> EventForm) = _state.update { s ->
        var f = change(s.form)
        // Ende nie vor Beginn: wird der Beginn verschoben, wandert das Ende mit.
        if (f.end.isBefore(f.start)) {
            val duration = java.time.Duration.between(s.form.start, s.form.end).coerceAtLeast(java.time.Duration.ofMinutes(30))
            val end = f.start.plus(duration)
            f = f.copy(endDate = end.toLocalDate(), endTime = end.toLocalTime())
        }
        s.copy(form = f, errors = emptyMap())
    }

    fun requestNotifications() = deps.notifications.request()

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        val f = s.form
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val draft = EventDraft(
                    id = eventId,
                    title = f.title,
                    start = f.start,
                    end = f.end,
                    allDay = f.allDay,
                    location = f.location,
                    note = f.note,
                    repeat = f.repeatUnit?.let { Repeat(it, f.repeatInterval) },
                )
                when (val r = deps.saveEvent(draft)) {
                    is PlanningResult.Success -> {
                        val id = r.value
                        deps.setReminder(id, f.reminderMinutes)
                        if (f.linkedTaskId != originalLinkedTask) {
                            originalLinkedTask?.let { deps.unlinkNodes(id, it) }
                            f.linkedTaskId?.let { deps.linkNodes(id, it, LinkRelation.RELATES_TO) }
                        }
                        val text = if (f.reminderMinutes != null) "Termin gespeichert · Erinnerung ${PlanningText.reminder(f.reminderMinutes)}" else "Termin gespeichert"
                        _state.update { it.copy(saving = false, outcome = EventOutcome.Saved(text)) }
                    }
                    is PlanningResult.Invalid -> _state.update {
                        it.copy(saving = false, errors = r.errors.associate { e -> fieldOf(e) to PlanningText.error(e) })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errors = mapOf(EventField.GENERAL to "Speichern fehlgeschlagen. Es wurde nichts verändert.")) }
            }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        val id = eventId ?: return
        viewModelScope.launch {
            try {
                val event = deps.repository.event(id)
                val reminders = deps.repository.reminders().filter { it.nodeId == id }
                when (deps.deleteEvent(id)) {
                    is PlanningResult.Success -> _state.update {
                        it.copy(confirmDelete = false, outcome = event?.let { e -> EventOutcome.Deleted(EventSnapshot(e, reminders)) } ?: EventOutcome.Saved("Termin gelöscht"))
                    }
                    is PlanningResult.Invalid -> _state.update { it.copy(confirmDelete = false, errors = mapOf(EventField.GENERAL to "Termin nicht gefunden.")) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(confirmDelete = false, errors = mapOf(EventField.GENERAL to "Löschen fehlgeschlagen. Es wurde nichts verändert.")) }
            }
        }
    }

    fun restore(snapshot: EventSnapshot, done: (Boolean) -> Unit) {
        deps.background.launch {
            val ok = try {
                deps.repository.saveEvent(snapshot.event, deps.clock.now())
                snapshot.reminders.forEach { deps.repository.saveReminder(it) }
                deps.scheduler.reschedule()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            done(ok)
        }
    }

    private fun fieldOf(e: PlanningError): EventField = when (e) {
        PlanningError.TitleRequired, PlanningError.TitleTooLong -> EventField.TITLE
        PlanningError.EndBeforeStart -> EventField.TIME
        else -> EventField.GENERAL
    }
}
