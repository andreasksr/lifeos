package app.lifeos.core.domain.planning

import app.lifeos.core.domain.home.AttentionItem
import app.lifeos.core.domain.home.AttentionSeverity
import app.lifeos.core.domain.home.AttentionTarget
import app.lifeos.core.model.CalendarEvent
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.NodeId
import app.lifeos.core.model.Priority
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

sealed interface AgendaItem {
    val id: NodeId
    val title: String
    /** Sortierzeit innerhalb des Tages; `null` = ganztägig/ohne Uhrzeit (zuerst). */
    val time: LocalTime?

    data class EventItem(val event: CalendarEvent, val occurrenceStart: LocalDateTime, val occurrenceEnd: LocalDateTime) : AgendaItem {
        override val id get() = event.id
        override val title get() = event.title
        override val time: LocalTime? get() = if (event.allDay) null else occurrenceStart.toLocalTime()
    }

    data class TaskItem(val task: Task, val overdue: Boolean) : AgendaItem {
        override val id get() = task.id
        override val title get() = task.title
        override val time: LocalTime? get() = task.dueTime
    }
}

data class AgendaDay(val date: LocalDate, val items: List<AgendaItem>)

/**
 * Baut die Agenda für einen Zeitraum: Termine (inkl. Wiederholungen) und fällige Aufgaben.
 * Aufgaben bleiben Aufgaben – sie werden am Fälligkeitstag gelistet, aber nicht als Termin behandelt.
 * Überfällige offene Aufgaben erscheinen am [today].
 */
object AgendaBuilder {

    fun build(
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        tasks: List<Task>,
        events: List<CalendarEvent>,
        includeDone: Boolean = false,
    ): List<AgendaDay> {
        val byDate = sortedMapOf<LocalDate, MutableList<AgendaItem>>()
        fun add(date: LocalDate, item: AgendaItem) {
            if (date.isBefore(from) || date.isAfter(to)) return
            byDate.getOrPut(date) { mutableListOf() } += item
        }
        events.forEach { e -> occurrences(e, from, to).forEach { (s, en) -> add(s.toLocalDate(), AgendaItem.EventItem(e, s, en)) } }
        tasks.forEach { t ->
            val due = t.dueDate ?: return@forEach
            if (t.status == TaskStatus.DONE && !includeDone) return@forEach
            val overdue = t.status == TaskStatus.OPEN && due.isBefore(today)
            add(if (overdue) today else due, AgendaItem.TaskItem(t, overdue))
        }
        return byDate.map { (d, items) ->
            AgendaDay(d, items.sortedWith(compareBy<AgendaItem>({ it.time != null }, { it.time }, { it.title.lowercase() })))
        }
    }

    /** Vorkommen eines Termins im Zeitraum (mehrtägige Termine nur am Beginn). */
    fun occurrences(e: CalendarEvent, from: LocalDate, to: LocalDate): List<Pair<LocalDateTime, LocalDateTime>> {
        val duration = java.time.Duration.between(e.start, e.end)
        val repeat = e.repeat ?: return if (!e.start.toLocalDate().isBefore(from) && !e.start.toLocalDate().isAfter(to)) listOf(e.start to e.end) else emptyList()
        val result = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        val anchor = e.start.toLocalDate()
        var k = 0L
        var guard = 0
        while (guard++ < 5_000) {
            val d = if (k == 0L) anchor else repeat.occurrence(anchor, k)
            if (d.isAfter(to)) break
            if (!d.isBefore(from)) {
                val s = d.atTime(e.start.toLocalTime())
                result += s to s.plus(duration)
            }
            k++
        }
        return result
    }

    /** Offene Aufgaben ohne Datum oder in der Zukunft – „Später". */
    fun backlog(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.filter { it.status == TaskStatus.OPEN && (it.dueDate?.isAfter(today) != false) }
            .sortedWith(compareByDescending<Task> { it.priority }.thenBy { it.dueDate ?: LocalDate.MAX }.thenBy { it.title.lowercase() })

    /** „Aufmerksamkeit erforderlich" aus der Planung: überfällige Aufgaben, wichtige heute fällige. */
    fun attention(tasks: List<Task>, today: LocalDate): List<AttentionItem> =
        tasks.filter { it.status == TaskStatus.OPEN && it.dueDate != null }.mapNotNull { t ->
            val due = t.dueDate!!
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
            when {
                days < 0 -> AttentionItem(t.title, "Aufgabe · ${LifeFormat.relativeDue(due, today)}", AttentionSeverity.ALERT, AttentionTarget.Node(t.id), days)
                days == 0L && t.priority == Priority.HIGH -> AttentionItem(t.title, "Wichtige Aufgabe · heute fällig", AttentionSeverity.WARN, AttentionTarget.Node(t.id), 0)
                else -> null
            }
        }
}
