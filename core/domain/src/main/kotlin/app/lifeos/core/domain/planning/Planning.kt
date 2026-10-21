package app.lifeos.core.domain.planning

import app.lifeos.core.domain.common.IdGenerator
import app.lifeos.core.domain.common.LifeClock
import app.lifeos.core.model.CalendarEvent
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Priority
import app.lifeos.core.model.Reminder
import app.lifeos.core.model.Repeat
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// ───────────────────────────── Ports ─────────────────────────────

data class TaskCompletion(val taskId: NodeId, val completedAt: Instant, val dueDate: LocalDate?)

interface PlanningRepository {
    fun observeTasks(): Flow<List<Task>>
    fun observeEvents(): Flow<List<CalendarEvent>>
    fun observeReminders(): Flow<List<Reminder>>
    suspend fun task(id: NodeId): Task?
    suspend fun event(id: NodeId): CalendarEvent?
    suspend fun tasks(): List<Task>
    suspend fun events(): List<CalendarEvent>
    suspend fun reminders(): List<Reminder>
    suspend fun saveTask(task: Task, now: Instant)
    suspend fun deleteTask(id: NodeId)
    suspend fun saveEvent(event: CalendarEvent, now: Instant)
    suspend fun deleteEvent(id: NodeId)
    suspend fun saveReminder(reminder: Reminder)
    suspend fun deleteReminder(id: NodeId)
    suspend fun recordCompletion(completion: TaskCompletion)
    fun observeCompletions(): Flow<List<TaskCompletion>>
}

/** Plant Android-Benachrichtigungen. Implementiert in der App (AlarmManager). */
fun interface ReminderScheduler {
    suspend fun reschedule()
}

// ───────────────────────────── Fehler / Ergebnis ─────────────────────────────

sealed interface PlanningError {
    data object TitleRequired : PlanningError
    data object TitleTooLong : PlanningError
    data object RepeatNeedsDueDate : PlanningError
    data object EndBeforeStart : PlanningError
    data object NotFound : PlanningError
    data object InvalidReminder : PlanningError
}

sealed interface PlanningResult<out T> {
    data class Success<T>(val value: T) : PlanningResult<T>
    data class Invalid(val errors: List<PlanningError>) : PlanningResult<Nothing>
}

// ───────────────────────────── Use-Cases Aufgaben ─────────────────────────────

data class TaskDraft(
    val id: NodeId? = null,
    val title: String,
    val description: String? = null,
    val priority: Priority = Priority.NORMAL,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val repeat: Repeat? = null,
    val projectId: NodeId? = null,
)

class SaveTask(
    private val repo: PlanningRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(draft: TaskDraft): PlanningResult<NodeId> {
        val errors = mutableListOf<PlanningError>()
        val title = draft.title.trim()
        if (title.isEmpty()) errors += PlanningError.TitleRequired
        if (title.length > 200) errors += PlanningError.TitleTooLong
        if (draft.repeat != null && draft.dueDate == null) errors += PlanningError.RepeatNeedsDueDate
        val existing = draft.id?.let { repo.task(it) }
        if (draft.id != null && existing == null) errors += PlanningError.NotFound
        if (errors.isNotEmpty()) return PlanningResult.Invalid(errors)
        val task = Task(
            id = draft.id ?: ids.newId(),
            title = title,
            description = draft.description?.trim()?.takeIf { it.isNotEmpty() },
            status = existing?.status ?: TaskStatus.OPEN,
            priority = draft.priority,
            dueDate = draft.dueDate,
            dueTime = draft.dueTime?.withSecond(0)?.withNano(0),
            repeat = draft.repeat,
            repeatAnchor = if (draft.repeat != null) {
                if (existing != null && existing.repeat == draft.repeat && existing.repeatAnchor != null) existing.repeatAnchor else draft.dueDate
            } else {
                null
            },
            projectId = draft.projectId,
            completedAt = existing?.completedAt,
            isSample = existing?.isSample ?: false,
        )
        repo.saveTask(task, clock.now())
        scheduler.reschedule()
        return PlanningResult.Success(task.id)
    }
}

/**
 * Erledigen. Wiederkehrende Aufgaben rücken auf die nächste Fälligkeit vor (bleiben offen), einmalige
 * werden DONE. In beiden Fällen wird die Erledigung protokolliert; Erinnerungen für erledigte
 * Aufgaben werden nicht mehr geplant.
 */
class CompleteTask(
    private val repo: PlanningRepository,
    private val clock: LifeClock,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(id: NodeId): PlanningResult<Task> {
        val task = repo.task(id) ?: return PlanningResult.Invalid(listOf(PlanningError.NotFound))
        if (task.status == TaskStatus.DONE) return PlanningResult.Success(task)
        val now = clock.now()
        repo.recordCompletion(TaskCompletion(task.id, now, task.dueDate))
        val repeat = task.repeat
        val due = task.dueDate
        val updated = if (repeat != null && due != null) {
            val anchor = task.repeatAnchor ?: due
            // Mindestens bis nach heute vorrücken – verpasste Termine werden nicht einzeln nachgeholt.
            val next = repeat.nextAfter(anchor, maxOf(due, clock.today().minusDays(1)))
            task.copy(dueDate = next, completedAt = now)
        } else {
            task.copy(status = TaskStatus.DONE, completedAt = now)
        }
        repo.saveTask(updated, now)
        // Bereits gezeigte Erinnerungen zurücksetzen, damit die nächste Fälligkeit erinnert wird.
        if (updated.status == TaskStatus.OPEN) {
            repo.reminders().filter { it.nodeId == id }.forEach { repo.saveReminder(it.copy(lastFiredFor = null, snoozedUntil = null)) }
        }
        scheduler.reschedule()
        return PlanningResult.Success(updated)
    }
}

class ReopenTask(private val repo: PlanningRepository, private val clock: LifeClock, private val scheduler: ReminderScheduler) {
    suspend operator fun invoke(id: NodeId): PlanningResult<Unit> {
        val task = repo.task(id) ?: return PlanningResult.Invalid(listOf(PlanningError.NotFound))
        repo.saveTask(task.copy(status = TaskStatus.OPEN, completedAt = null), clock.now())
        scheduler.reschedule()
        return PlanningResult.Success(Unit)
    }
}

class DeleteTask(private val repo: PlanningRepository, private val scheduler: ReminderScheduler) {
    suspend operator fun invoke(id: NodeId): PlanningResult<Task> {
        val task = repo.task(id) ?: return PlanningResult.Invalid(listOf(PlanningError.NotFound))
        repo.deleteTask(id) // Kaskade: Erinnerungen, Links
        scheduler.reschedule()
        return PlanningResult.Success(task)
    }
}

// ───────────────────────────── Use-Cases Termine ─────────────────────────────

data class EventDraft(
    val id: NodeId? = null,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val location: String? = null,
    val note: String? = null,
    val repeat: Repeat? = null,
)

class SaveEvent(
    private val repo: PlanningRepository,
    private val ids: IdGenerator,
    private val clock: LifeClock,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(draft: EventDraft): PlanningResult<NodeId> {
        val errors = mutableListOf<PlanningError>()
        val title = draft.title.trim()
        if (title.isEmpty()) errors += PlanningError.TitleRequired
        if (title.length > 200) errors += PlanningError.TitleTooLong
        val start = if (draft.allDay) draft.start.toLocalDate().atStartOfDay() else draft.start
        val end = if (draft.allDay) draft.end.toLocalDate().atTime(23, 59) else draft.end
        if (end.isBefore(start)) errors += PlanningError.EndBeforeStart
        if (draft.id != null && repo.event(draft.id) == null) errors += PlanningError.NotFound
        if (errors.isNotEmpty()) return PlanningResult.Invalid(errors)
        val existing = draft.id?.let { repo.event(it) }
        val event = CalendarEvent(
            id = draft.id ?: ids.newId(),
            title = title,
            start = start.withSecond(0).withNano(0),
            end = end.withSecond(0).withNano(0),
            allDay = draft.allDay,
            location = draft.location?.trim()?.takeIf { it.isNotEmpty() },
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            repeat = draft.repeat,
            isSample = existing?.isSample ?: false,
        )
        repo.saveEvent(event, clock.now())
        scheduler.reschedule()
        return PlanningResult.Success(event.id)
    }
}

class DeleteEvent(private val repo: PlanningRepository, private val scheduler: ReminderScheduler) {
    suspend operator fun invoke(id: NodeId): PlanningResult<Unit> {
        repo.event(id) ?: return PlanningResult.Invalid(listOf(PlanningError.NotFound))
        repo.deleteEvent(id)
        scheduler.reschedule()
        return PlanningResult.Success(Unit)
    }
}

// ───────────────────────────── Erinnerungen ─────────────────────────────

class SetReminder(
    private val repo: PlanningRepository,
    private val ids: IdGenerator,
    private val scheduler: ReminderScheduler,
) {
    /** Ersetzt alle Erinnerungen eines Eintrags durch genau eine mit [minutesBefore] (null = keine). */
    suspend operator fun invoke(nodeId: NodeId, minutesBefore: Int?): PlanningResult<Unit> {
        if (minutesBefore != null && minutesBefore !in 0..(60 * 24 * 14)) return PlanningResult.Invalid(listOf(PlanningError.InvalidReminder))
        repo.reminders().filter { it.nodeId == nodeId }.forEach { repo.deleteReminder(it.id) }
        if (minutesBefore != null) repo.saveReminder(Reminder(ids.newId(), nodeId, minutesBefore))
        scheduler.reschedule()
        return PlanningResult.Success(Unit)
    }
}

class SnoozeReminder(private val repo: PlanningRepository, private val clock: LifeClock, private val scheduler: ReminderScheduler) {
    suspend operator fun invoke(reminderId: NodeId, duration: Duration) {
        val r = repo.reminders().firstOrNull { it.id == reminderId } ?: return
        repo.saveReminder(r.copy(snoozedUntil = clock.now().plus(duration)))
        scheduler.reschedule()
    }
}

class MarkReminderFired(private val repo: PlanningRepository, private val scheduler: ReminderScheduler) {
    suspend operator fun invoke(reminderId: NodeId, firedFor: Instant) {
        val r = repo.reminders().firstOrNull { it.id == reminderId } ?: return
        repo.saveReminder(r.copy(lastFiredFor = firedFor, snoozedUntil = null))
        scheduler.reschedule()
    }
}

/** Ein konkret zu planender Alarm. */
data class PlannedAlarm(
    val reminderId: NodeId,
    val nodeId: NodeId,
    /** Zeitpunkt der Fälligkeit/des Beginns, auf den sich die Erinnerung bezieht (für „bereits gezeigt"). */
    val firesFor: Instant,
    val triggerAt: Instant,
    val title: String,
    val text: String,
    val isTask: Boolean,
)

/**
 * Berechnet, welche Erinnerungen wann ausgelöst werden müssen – rein, deterministisch, testbar.
 * Regeln: erledigte Aufgaben → keine; deaktiviert → keine; bereits für diesen Termin gezeigt → keine;
 * verpasste (z. B. Gerät war aus) → sofort, sofern der Termin noch nicht länger als 1 Tag vorbei ist.
 */
object ReminderPlanner {

    fun plan(
        reminders: List<Reminder>,
        tasks: List<Task>,
        events: List<CalendarEvent>,
        now: Instant,
        zone: ZoneId,
    ): List<PlannedAlarm> {
        val taskById = tasks.associateBy { it.id }
        val eventById = events.associateBy { it.id }
        return reminders.filter { it.enabled }.mapNotNull { r ->
            val task = taskById[r.nodeId]
            val event = eventById[r.nodeId]
            val (reference, title, text) = when {
                task != null -> {
                    if (task.status == TaskStatus.DONE) return@mapNotNull null
                    val due = task.dueDate ?: return@mapNotNull null
                    val time = task.dueTime ?: Reminder.DEFAULT_TASK_TIME
                    Triple(due.atTime(time).atZone(zone).toInstant(), task.title, "Aufgabe fällig ${formatTime(due, task.dueTime)}")
                }
                event != null -> {
                    val start = nextEventStart(event, now, r.minutesBefore, zone) ?: return@mapNotNull null
                    Triple(start.atZone(zone).toInstant(), event.title, "Termin ${formatTime(start.toLocalDate(), if (event.allDay) null else start.toLocalTime())}" + (event.location?.let { " · $it" } ?: ""))
                }
                else -> return@mapNotNull null
            }
            if (r.lastFiredFor != null && !reference.isAfter(r.lastFiredFor)) return@mapNotNull null
            var trigger = reference.minusSeconds(r.minutesBefore * 60L)
            r.snoozedUntil?.let { if (it.isAfter(now)) trigger = it }
            if (trigger.isBefore(now)) {
                if (reference.isBefore(now.minus(Duration.ofDays(1)))) return@mapNotNull null
                trigger = now.plusSeconds(5)
            }
            PlannedAlarm(r.id, r.nodeId, reference, trigger, title, text, isTask = task != null)
        }.sortedBy { it.triggerAt }
    }

    /** Nächster Beginn eines (ggf. wiederkehrenden) Termins, dessen Erinnerung noch nicht vorbei ist. */
    fun nextEventStart(event: CalendarEvent, now: Instant, minutesBefore: Int, zone: ZoneId): LocalDateTime? {
        val threshold = LocalDateTime.ofInstant(now.plusSeconds(minutesBefore * 60L).minus(Duration.ofDays(1)), zone)
        val repeat = event.repeat ?: return event.start
        if (!event.start.isBefore(threshold)) return event.start
        val anchor = event.start.toLocalDate()
        val nextDate = repeat.nextAfter(anchor, threshold.toLocalDate().minusDays(1))
        return nextDate.atTime(event.start.toLocalTime())
    }

    private fun formatTime(date: LocalDate, time: LocalTime?): String =
        app.lifeos.core.model.LifeFormat.numericDate(date) + (time?.let { " · " + app.lifeos.core.model.LifeFormat.time(it) } ?: "")
}
