package app.lifeos.core.domain.planning

import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.CalendarEvent
import app.lifeos.core.model.Priority
import app.lifeos.core.model.Repeat
import app.lifeos.core.model.RepeatUnit
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskGroupingTest {
    private val today = LocalDate.of(2026, 10, 21)

    private fun t(n: Int, due: LocalDate?, prio: Priority = Priority.NORMAL, status: TaskStatus = TaskStatus.OPEN, time: LocalTime? = null, done: Instant? = null) =
        Task(id(n), "T$n", priority = prio, dueDate = due, dueTime = time, status = status, completedAt = done)

    @Test
    fun `jede offene aufgabe genau einer gruppe`() {
        val tasks = listOf(
            t(1, today.minusDays(3)), t(2, today.minusDays(1), Priority.HIGH), t(3, today), t(4, today, Priority.HIGH),
            t(5, today.plusDays(2)), t(6, null), t(7, null, Priority.HIGH),
            t(8, today, status = TaskStatus.DONE, done = Instant.parse("2026-10-21T08:00:00Z")),
            t(9, null, status = TaskStatus.DONE, done = Instant.parse("2026-10-21T09:00:00Z")),
        )
        val g = TaskGrouping.group(tasks, today)
        assertEquals(listOf("T1", "T2"), g.overdue.map { it.title })
        assertEquals(listOf("T4", "T3"), g.today.map { it.title })
        assertEquals(listOf("T5"), g.upcoming.map { it.title })
        assertEquals(listOf("T7", "T6"), g.someday.map { it.title })
        assertEquals(listOf("T9", "T8"), g.done.map { it.title })
        assertEquals(7, g.openCount)
    }

    @Test
    fun `wochenbeginn ist montag`() {
        assertEquals(LocalDate.of(2026, 10, 19), TaskGrouping.weekStart(today))
        assertEquals(LocalDate.of(2026, 10, 19), TaskGrouping.weekStart(LocalDate.of(2026, 10, 25)))
        assertEquals(LocalDate.of(2026, 10, 26), TaskGrouping.weekStart(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `naechste termine mit wiederholung und ohne vergangene`() {
        val weekly = CalendarEvent(id(20), "Unterricht", LocalDateTime.of(2026, 10, 7, 18, 0), LocalDateTime.of(2026, 10, 7, 21, 0), repeat = Repeat(RepeatUnit.WEEK))
        val past = CalendarEvent(id(21), "Vorbei", LocalDateTime.of(2026, 10, 21, 7, 0), LocalDateTime.of(2026, 10, 21, 8, 0))
        val later = CalendarEvent(id(22), "Werkstatt", LocalDateTime.of(2026, 10, 22, 9, 0), LocalDateTime.of(2026, 10, 22, 10, 0))
        val next = TaskGrouping.upcomingEvents(listOf(weekly, past, later), LocalDateTime.of(2026, 10, 21, 12, 0), 14, 3)
        assertEquals(listOf("Unterricht", "Werkstatt", "Unterricht"), next.map { it.title })
        assertEquals(LocalDateTime.of(2026, 10, 21, 18, 0), next.first().occurrenceStart)
    }
}
