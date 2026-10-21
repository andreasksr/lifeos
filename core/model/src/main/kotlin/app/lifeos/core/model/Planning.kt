package app.lifeos.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TaskStatus { OPEN, DONE }

enum class Priority { LOW, NORMAL, HIGH }

/** Wiederholungseinheit für Aufgaben und Termine. */
enum class RepeatUnit { DAY, WEEK, MONTH, YEAR }

data class Repeat(val unit: RepeatUnit, val interval: Int = 1) {
    init {
        require(interval in 1..365) { "Intervall 1–365" }
    }

    /** Nächster Termin nach [from] – vom [anchor] aus gerechnet, damit Monatsenden nicht wandern. */
    fun nextAfter(anchor: LocalDate, from: LocalDate): LocalDate {
        var k = 1L
        while (true) {
            val d = occurrence(anchor, k)
            if (d.isAfter(from)) return d
            k++
            require(k < 100_000) { "Keine Folgefälligkeit gefunden" }
        }
    }

    fun occurrence(anchor: LocalDate, k: Long): LocalDate = when (unit) {
        RepeatUnit.DAY -> anchor.plusDays(k * interval)
        RepeatUnit.WEEK -> anchor.plusWeeks(k * interval)
        RepeatUnit.MONTH -> anchor.plusMonths(k * interval)
        RepeatUnit.YEAR -> anchor.plusYears(k * interval)
    }
}

/**
 * Aufgabe. Eine Aufgabe ist KEIN Termin: sie hat höchstens ein Fälligkeitsdatum (optional mit Uhrzeit).
 * Wiederkehrende Aufgaben rücken beim Erledigen auf die nächste Fälligkeit vor.
 */
data class Task(
    val id: NodeId,
    val title: String,
    val description: String? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val priority: Priority = Priority.NORMAL,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val repeat: Repeat? = null,
    /** Bezugsdatum der Wiederholung (erste Fälligkeit). */
    val repeatAnchor: LocalDate? = null,
    val projectId: NodeId? = null,
    val completedAt: Instant? = null,
    val isSample: Boolean = false,
)

/** Kalendereintrag mit festem Zeitraum. */
data class CalendarEvent(
    val id: NodeId,
    val title: String,
    val start: java.time.LocalDateTime,
    val end: java.time.LocalDateTime,
    val allDay: Boolean = false,
    val location: String? = null,
    val note: String? = null,
    val repeat: Repeat? = null,
    val isSample: Boolean = false,
) {
    init {
        require(!end.isBefore(start)) { "Ende vor Beginn" }
    }
}

/**
 * Erinnerung an eine Aufgabe oder einen Termin: [minutesBefore] relativ zur Fälligkeit/zum Beginn.
 * Aufgaben ohne Uhrzeit gelten um [DEFAULT_TASK_TIME] als fällig.
 */
data class Reminder(
    val id: NodeId,
    val nodeId: NodeId,
    val minutesBefore: Int,
    val enabled: Boolean = true,
    /** Auslösezeitpunkt, für den bereits eine Benachrichtigung gezeigt wurde (gegen Doppelungen). */
    val lastFiredFor: Instant? = null,
    /** „Später erinnern": überschreibt den nächsten Auslösezeitpunkt einmalig. */
    val snoozedUntil: Instant? = null,
) {
    companion object {
        val DEFAULT_TASK_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

enum class ProjectStatus { PLANNED, ACTIVE, PAUSED, DONE, CANCELLED }

/** Wie der Projektfortschritt bestimmt wird – nie willkürlich. */
enum class ProgressMode {
    /** Anteil erledigter Aufgaben. */
    TASKS,

    /** Anteil erreichter Meilensteine. */
    MILESTONES,

    /** Bewusst vom Nutzer gesetzt. */
    MANUAL,
}

data class Project(
    val id: NodeId,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val startDate: LocalDate,
    val targetDate: LocalDate? = null,
    val progressMode: ProgressMode = ProgressMode.TASKS,
    /** Nur bei [ProgressMode.MANUAL]: 0..1000 Promille. */
    val manualProgressPermille: Int? = null,
    /** Optionales Projektbudget (Minor Units). */
    val budgetMinor: Long? = null,
    val notes: String? = null,
    val isSample: Boolean = false,
)

data class Milestone(
    val id: NodeId,
    val projectId: NodeId,
    val title: String,
    val dueDate: LocalDate? = null,
    val doneAt: Instant? = null,
    val sortOrder: Int = 0,
)
