package app.lifeos.core.domain.planning

import app.lifeos.core.domain.testing.CountingScheduler
import app.lifeos.core.domain.testing.FakePlanningRepository
import app.lifeos.core.domain.testing.FixedClock
import app.lifeos.core.domain.testing.SequentialIds
import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.CalendarEvent
import app.lifeos.core.model.Priority
import app.lifeos.core.model.Reminder
import app.lifeos.core.model.Repeat
import app.lifeos.core.model.RepeatUnit
import app.lifeos.core.model.Task
import app.lifeos.core.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class PlanningTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 20)
    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val clock = FixedClock(now, today)
    private val ids = SequentialIds()

    @Test
    fun `aufgabe validierung`() = runBlocking {
        val repo = FakePlanningRepository()
        val save = SaveTask(repo, ids, clock, CountingScheduler())
        assertEquals(PlanningResult.Invalid(listOf(PlanningError.TitleRequired)), save(TaskDraft(title = "  ")))
        assertEquals(
            PlanningResult.Invalid(listOf(PlanningError.RepeatNeedsDueDate)),
            save(TaskDraft(title = "Müll", repeat = Repeat(RepeatUnit.WEEK))),
        )
        assertTrue(save(TaskDraft(title = "Ölwechsel", dueDate = today)) is PlanningResult.Success)
    }

    @Test
    fun `einmalige aufgabe erledigen deaktiviert erinnerungen`() = runBlocking {
        val repo = FakePlanningRepository()
        val sched = CountingScheduler()
        val tid = (SaveTask(repo, ids, clock, sched)(TaskDraft(title = "Reifen wechseln", dueDate = today.plusDays(1))) as PlanningResult.Success).value
        SetReminder(repo, ids, sched)(tid, 60)
        assertEquals(1, ReminderPlanner.plan(repo.reminders(), repo.tasks(), emptyList(), now, zone).size)

        val done = (CompleteTask(repo, clock, sched)(tid) as PlanningResult.Success).value
        assertEquals(TaskStatus.DONE, done.status)
        // Erledigte Aufgabe erzeugt keine Benachrichtigung mehr (Auftrag Teil 15)
        assertTrue(ReminderPlanner.plan(repo.reminders(), repo.tasks(), emptyList(), now, zone).isEmpty())
        assertEquals(1, repo.completions.value.size)
        assertTrue(sched.calls >= 3)
    }

    @Test
    fun `wiederkehrende aufgabe rueckt vor und erinnert erneut`() = runBlocking {
        val repo = FakePlanningRepository()
        val sched = CountingScheduler()
        val tid = (SaveTask(repo, ids, clock, sched)(
            TaskDraft(title = "Müll rausbringen", dueDate = LocalDate.of(2026, 10, 19), repeat = Repeat(RepeatUnit.WEEK)),
        ) as PlanningResult.Success).value
        SetReminder(repo, ids, sched)(tid, 0)
        val r = repo.reminders().single()
        repo.saveReminder(r.copy(lastFiredFor = Instant.parse("2026-10-19T09:00:00Z")))

        val next = (CompleteTask(repo, clock, sched)(tid) as PlanningResult.Success).value
        assertEquals(TaskStatus.OPEN, next.status)
        assertEquals(LocalDate.of(2026, 10, 26), next.dueDate)
        assertNull(repo.reminders().single().lastFiredFor)
        val alarm = ReminderPlanner.plan(repo.reminders(), repo.tasks(), emptyList(), now, zone).single()
        assertEquals(Instant.parse("2026-10-26T09:00:00Z"), alarm.triggerAt)
    }

    @Test
    fun `erinnerungsplanung regeln`() {
        val task = Task(id(1), "Steuer", dueDate = today, dueTime = LocalTime.of(14, 0))
        val r = Reminder(id(2), task.id, minutesBefore = 30)
        assertEquals(Instant.parse("2026-10-20T13:30:00Z"), ReminderPlanner.plan(listOf(r), listOf(task), emptyList(), now, zone).single().triggerAt)
        // Bereits gezeigt → nicht erneut
        assertTrue(ReminderPlanner.plan(listOf(r.copy(lastFiredFor = Instant.parse("2026-10-20T14:00:00Z"))), listOf(task), emptyList(), now, zone).isEmpty())
        // Deaktiviert → nicht
        assertTrue(ReminderPlanner.plan(listOf(r.copy(enabled = false)), listOf(task), emptyList(), now, zone).isEmpty())
        // Verschoben → Schlummerzeit
        val snoozed = r.copy(snoozedUntil = now.plus(Duration.ofHours(1)))
        assertEquals(now.plus(Duration.ofHours(1)), ReminderPlanner.plan(listOf(snoozed), listOf(task), emptyList(), now, zone).single().triggerAt)
        // Verpasst (Gerät war aus) → sofort
        val past = task.copy(dueTime = LocalTime.of(8, 0))
        assertEquals(now.plusSeconds(5), ReminderPlanner.plan(listOf(r), listOf(past), emptyList(), now, zone).single().triggerAt)
        // Sehr alt → verworfen
        val old = task.copy(dueDate = today.minusDays(5))
        assertTrue(ReminderPlanner.plan(listOf(r), listOf(old), emptyList(), now, zone).isEmpty())
        // Ohne Uhrzeit → 09:00
        val noTime = task.copy(dueDate = today.plusDays(1), dueTime = null)
        assertEquals(Instant.parse("2026-10-21T08:30:00Z"), ReminderPlanner.plan(listOf(r), listOf(noTime), emptyList(), now, zone).single().triggerAt)
    }

    @Test
    fun `wiederkehrender termin erinnert an naechstes vorkommen`() {
        val e = CalendarEvent(id(1), "Unterricht", LocalDateTime.of(2026, 9, 1, 18, 0), LocalDateTime.of(2026, 9, 1, 21, 0), repeat = Repeat(RepeatUnit.WEEK))
        val r = Reminder(id(2), e.id, minutesBefore = 60)
        val alarm = ReminderPlanner.plan(listOf(r), emptyList(), listOf(e), now, zone).single()
        assertEquals(Instant.parse("2026-10-20T17:00:00Z"), alarm.triggerAt) // 20.10. ist ein Dienstag wie der 01.09.
    }

    @Test
    fun `nach ausloesen wird das naechste vorkommen eines wiederkehrenden termins geplant`() {
        val e = CalendarEvent(id(1), "Unterricht", LocalDateTime.of(2026, 9, 1, 18, 0), LocalDateTime.of(2026, 9, 1, 21, 0), repeat = Repeat(RepeatUnit.WEEK))
        val fired = Reminder(id(2), e.id, minutesBefore = 60, lastFiredFor = Instant.parse("2026-10-20T18:00:00Z"))
        val later = Instant.parse("2026-10-20T17:05:00Z")
        val alarm = ReminderPlanner.plan(listOf(fired), emptyList(), listOf(e), later, zone).single()
        assertEquals(Instant.parse("2026-10-27T17:00:00Z"), alarm.triggerAt)
        assertEquals(Instant.parse("2026-10-27T18:00:00Z"), alarm.firesFor)
    }

    @Test
    fun `agenda trennt aufgaben und termine und zeigt ueberfaelliges heute`() {
        val tasks = listOf(
            Task(id(1), "Rechnung zahlen", dueDate = today.minusDays(2)),
            Task(id(2), "Öl kaufen", dueDate = today.plusDays(1), dueTime = LocalTime.of(10, 0)),
            Task(id(3), "Erledigt", dueDate = today, status = TaskStatus.DONE),
            Task(id(4), "Irgendwann"),
        )
        val events = listOf(
            CalendarEvent(id(10), "Zahnarzt", LocalDateTime.of(2026, 10, 20, 8, 30), LocalDateTime.of(2026, 10, 20, 9, 0)),
            CalendarEvent(id(11), "Urlaub", LocalDateTime.of(2026, 10, 21, 0, 0), LocalDateTime.of(2026, 10, 21, 23, 59), allDay = true),
        )
        val agenda = AgendaBuilder.build(today, today.plusDays(1), today, tasks, events)
        assertEquals(2, agenda.size)
        val day0 = agenda[0].items
        assertEquals(listOf("Rechnung zahlen", "Zahnarzt"), day0.map { it.title })
        assertTrue((day0[0] as AgendaItem.TaskItem).overdue)
        assertEquals(listOf("Urlaub", "Öl kaufen"), agenda[1].items.map { it.title }) // ganztägig zuerst
        assertEquals(listOf("Öl kaufen", "Irgendwann"), AgendaBuilder.backlog(tasks, today).map { it.title })
    }

    @Test
    fun `wiederholte termine im zeitraum`() {
        val e = CalendarEvent(id(1), "Unterricht", LocalDateTime.of(2026, 10, 6, 18, 0), LocalDateTime.of(2026, 10, 6, 21, 0), repeat = Repeat(RepeatUnit.WEEK, 2))
        val occ = AgendaBuilder.occurrences(e, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30))
        assertEquals(listOf(6, 20, 3, 17), occ.map { it.first.dayOfMonth })
    }

    @Test
    fun `aufmerksamkeit aus planung`() {
        val tasks = listOf(
            Task(id(1), "Überfällig", dueDate = today.minusDays(1)),
            Task(id(2), "Wichtig heute", dueDate = today, priority = Priority.HIGH),
            Task(id(3), "Normal heute", dueDate = today),
        )
        assertEquals(listOf("Überfällig", "Wichtig heute"), AgendaBuilder.attention(tasks, today).map { it.title })
    }

    @Test
    fun `termin validierung und ganztags normalisierung`() = runBlocking {
        val repo = FakePlanningRepository()
        val save = SaveEvent(repo, ids, clock, CountingScheduler())
        val start = LocalDateTime.of(2026, 10, 22, 10, 0)
        assertEquals(PlanningResult.Invalid(listOf(PlanningError.EndBeforeStart)), save(EventDraft(title = "X", start = start, end = start.minusHours(1))))
        val eid = (save(EventDraft(title = "Messe", start = start, end = start, allDay = true)) as PlanningResult.Success).value
        val e = repo.event(eid)!!
        assertEquals(LocalTime.MIDNIGHT, e.start.toLocalTime())
        assertEquals(LocalTime.of(23, 59), e.end.toLocalTime())
    }
}
