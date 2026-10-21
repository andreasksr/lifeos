package app.lifeos.feature.planning.presentation

import app.lifeos.core.domain.planning.PlanningError
import app.lifeos.core.model.Repeat
import app.lifeos.core.model.RepeatUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PlanningTextTest {
    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun `wiederholung lesbar`() {
        assertEquals("Keine", PlanningText.repeat(null))
        assertEquals("Wöchentlich", PlanningText.repeat(Repeat(RepeatUnit.WEEK)))
        assertEquals("Alle 2 Wochen", PlanningText.repeat(Repeat(RepeatUnit.WEEK, 2)))
        assertEquals("Alle 3 Monate", PlanningText.repeat(Repeat(RepeatUnit.MONTH, 3)))
    }

    @Test
    fun `erinnerungen`() {
        assertEquals(listOf("Keine", "Zum Zeitpunkt", "15 Min vorher", "1 Std vorher", "1 Tag vorher"), PlanningText.reminderOptions.map { PlanningText.reminder(it) })
        assertEquals("2 Std vorher", PlanningText.reminder(120))
        assertEquals("3 Tage vorher", PlanningText.reminder(3 * 24 * 60))
    }

    @Test
    fun `faelligkeit und zeitraum`() {
        assertEquals("Ohne Datum", PlanningText.due(null, null, today))
        assertEquals("Heute · 09:05", PlanningText.due(today, LocalTime.of(9, 5), today))
        assertEquals("Morgen", PlanningText.due(today.plusDays(1), null, today))
        val s = LocalDateTime.of(2026, 10, 21, 18, 0)
        assertEquals("18:00–21:00", PlanningText.eventSpan(s, s.plusHours(3), false, today))
        assertEquals("ganztägig", PlanningText.eventSpan(s.toLocalDate().atStartOfDay(), s.toLocalDate().atTime(23, 59), true, today))
        assertEquals("18:00 bis Morgen 09:00", PlanningText.eventSpan(s, LocalDateTime.of(2026, 10, 22, 9, 0), false, today))
    }

    @Test
    fun `jeder fehler hat einen text`() {
        listOf(PlanningError.TitleRequired, PlanningError.TitleTooLong, PlanningError.RepeatNeedsDueDate, PlanningError.EndBeforeStart, PlanningError.NotFound, PlanningError.InvalidReminder)
            .forEach { assertTrue(PlanningText.error(it).isNotBlank()) }
    }
}
