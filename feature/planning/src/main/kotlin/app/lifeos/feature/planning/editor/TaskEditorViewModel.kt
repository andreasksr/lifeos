package app.lifeos.feature.planning.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifeos.core.domain.planning.PlanningError
import app.lifeos.core.domain.planning.PlanningResult
import app.lifeos.core.domain.planning.TaskDraft
import app.lifeos.core.model.CalendarEvent
import app.lifeos.core.model.LinkRelation
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Priority
import app.lifeos.core.model.Project
import app.lifeos.core.model.ProjectStatus
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
import java.time.LocalTime

enum class TaskField { TITLE, DUE, REMINDER, GENERAL }

data class TaskForm(
    val title: String = "",
    val description: String = "",
    val priority: Priority = Priority.NORMAL,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val repeatUnit: RepeatUnit? = null,
    val repeatInterval: Int = 1,
    val projectId: NodeId? = null,
    val reminderMinutes: Int? = null,
) {
    val repeat: Repeat? get() = repeatUnit?.let { Repeat(it, repeatInterval) }
}

/** Schnappschuss für „Rückgängig" nach dem Löschen. */
data class TaskSnapshot(val task: Task, val reminders: List<Reminder>)

sealed interface TaskOutcome {
    data class Saved(val text: String) : TaskOutcome
    data class Deleted(val snapshot: TaskSnapshot) : TaskOutcome
}

data class TaskEditorState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val status: TaskStatus = TaskStatus.OPEN,
    val form: TaskForm = TaskForm(),
    val projects: List<Project> = emptyList(),
    val linkedEvents: List<CalendarEvent> = emptyList(),
    val today: LocalDate,
    val errors: Map<TaskField, String> = emptyMap(),
    val saving: Boolean = false,
    val confirmDelete: Boolean = false,
    val outcome: TaskOutcome? = null,
)

class TaskEditorViewModel(
    private val deps: PlanningDependencies,
    private val taskId: NodeId?,
    presetDate: LocalDate?,
    presetProject: NodeId?,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskEditorState(isEdit = taskId != null, today = deps.clock.today()))
    val state: StateFlow<TaskEditorState> = _state.asStateFlow()

    val notificationsAllowed: StateFlow<Boolean> = deps.notifications.allowed

    init {
        viewModelScope.launch {
            try {
                val projects = deps.projects.projects()
                val existing = taskId?.let { deps.repository.task(it) }
                val reminder = taskId?.let { id -> deps.repository.reminders().firstOrNull { it.nodeId == id && it.enabled } }
                val form = existing?.let {
                    TaskForm(
                        title = it.title,
                        description = it.description.orEmpty(),
                        priority = it.priority,
                        dueDate = it.dueDate,
                        dueTime = it.dueTime,
                        repeatUnit = it.repeat?.unit,
                        repeatInterval = it.repeat?.interval ?: 1,
                        projectId = it.projectId,
                        reminderMinutes = reminder?.minutesBefore,
                    )
                } ?: TaskForm(dueDate = presetDate, projectId = presetProject)
                val linked = taskId?.let { id ->
                    deps.links.linksOf(id).filter { it.relation == LinkRelation.RELATES_TO }
                        .mapNotNull { l -> deps.repository.event(if (l.from == id) l.to else l.from) }
                }.orEmpty()
                _state.update {
                    it.copy(
                        loading = false,
                        status = existing?.status ?: TaskStatus.OPEN,
                        form = form,
                        projects = projects.filter { p -> p.status == ProjectStatus.ACTIVE || p.status == ProjectStatus.PLANNED || p.id == form.projectId },
                        linkedEvents = linked,
                        errors = if (taskId != null && existing == null) mapOf(TaskField.GENERAL to "Diese Aufgabe existiert nicht mehr.") else emptyMap(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, errors = mapOf(TaskField.GENERAL to "Daten konnten nicht geladen werden.")) }
            }
        }
    }

    fun update(change: (TaskForm) -> TaskForm) = _state.update { s ->
        val f = change(s.form)
        // Ohne Datum gibt es weder Uhrzeit, Wiederholung noch Erinnerung.
        val clean = if (f.dueDate == null) f.copy(dueTime = null, repeatUnit = null, reminderMinutes = null) else f
        s.copy(form = clean, errors = emptyMap())
    }

    fun requestNotifications() = deps.notifications.request()

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        val f = s.form
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val draft = TaskDraft(
                    id = taskId,
                    title = f.title,
                    description = f.description,
                    priority = f.priority,
                    dueDate = f.dueDate,
                    dueTime = f.dueTime,
                    repeat = f.repeat,
                    projectId = f.projectId,
                )
                when (val r = deps.saveTask(draft)) {
                    is PlanningResult.Success -> {
                        deps.setReminder(r.value, if (f.dueDate != null) f.reminderMinutes else null)
                        val text = when {
                            f.reminderMinutes != null && f.dueDate != null -> "Aufgabe gespeichert · Erinnerung ${PlanningText.reminder(f.reminderMinutes)}"
                            else -> "Aufgabe gespeichert"
                        }
                        _state.update { it.copy(saving = false, outcome = TaskOutcome.Saved(text)) }
                    }
                    is PlanningResult.Invalid -> _state.update { it.copy(saving = false, errors = r.errors.associate { e -> fieldOf(e) to PlanningText.error(e) }) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errors = mapOf(TaskField.GENERAL to "Speichern fehlgeschlagen. Es wurde nichts verändert.")) }
            }
        }
    }

    fun toggleDone() {
        val id = taskId ?: return
        viewModelScope.launch {
            try {
                if (_state.value.status == TaskStatus.DONE) {
                    deps.reopenTask(id)
                    _state.update { it.copy(outcome = TaskOutcome.Saved("Aufgabe wieder offen")) }
                } else {
                    when (val r = deps.completeTask(id)) {
                        is PlanningResult.Success -> {
                            val next = r.value
                            val text = if (next.status == TaskStatus.OPEN && next.dueDate != null) {
                                "Erledigt – nächste Fälligkeit ${app.lifeos.core.model.LifeFormat.numericDate(next.dueDate!!)}"
                            } else {
                                "Aufgabe erledigt"
                            }
                            _state.update { it.copy(outcome = TaskOutcome.Saved(text)) }
                        }
                        is PlanningResult.Invalid -> _state.update { it.copy(errors = mapOf(TaskField.GENERAL to "Aufgabe nicht gefunden.")) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(errors = mapOf(TaskField.GENERAL to "Speichern fehlgeschlagen.")) }
            }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        val id = taskId ?: return
        viewModelScope.launch {
            try {
                val reminders = deps.repository.reminders().filter { it.nodeId == id }
                when (val r = deps.deleteTask(id)) {
                    is PlanningResult.Success -> _state.update { it.copy(confirmDelete = false, outcome = TaskOutcome.Deleted(TaskSnapshot(r.value, reminders))) }
                    is PlanningResult.Invalid -> _state.update { it.copy(confirmDelete = false, errors = mapOf(TaskField.GENERAL to "Aufgabe nicht gefunden.")) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(confirmDelete = false, errors = mapOf(TaskField.GENERAL to "Löschen fehlgeschlagen. Es wurde nichts verändert.")) }
            }
        }
    }

    /** Stellt eine gelöschte Aufgabe inkl. Erinnerungen wieder her (läuft über den Bildschirm hinaus). */
    fun restore(snapshot: TaskSnapshot, done: (Boolean) -> Unit) {
        deps.background.launch {
            val ok = try {
                deps.repository.saveTask(snapshot.task, deps.clock.now())
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

    private fun fieldOf(e: PlanningError): TaskField = when (e) {
        PlanningError.TitleRequired, PlanningError.TitleTooLong -> TaskField.TITLE
        PlanningError.RepeatNeedsDueDate, PlanningError.EndBeforeStart -> TaskField.DUE
        PlanningError.InvalidReminder -> TaskField.REMINDER
        PlanningError.NotFound -> TaskField.GENERAL
    }
}
