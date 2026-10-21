package app.lifeos.core.domain.ledger

import app.lifeos.core.domain.testing.id
import app.lifeos.core.model.Money
import app.lifeos.core.model.RecurringBooking
import app.lifeos.core.model.RecurringPayment
import app.lifeos.core.model.RecurringStatus
import app.lifeos.core.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringScheduleTest {

    private fun plan(anchor: String, interval: Int = 1, end: String? = null, active: Boolean = true, kind: TransactionKind = TransactionKind.EXPENSE) =
        RecurringPayment(
            id = id(1), name = "Miete", kind = kind, accountId = id(2), categoryId = id(3),
            amount = Money.eur(95_000), intervalMonths = interval, anchorDate = LocalDate.parse(anchor),
            endDate = end?.let(LocalDate::parse), active = active,
        )

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `monatlich am monatsende klemmt ohne zu wandern`() {
        assertEquals(
            listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30")),
            RecurringSchedule.dueDates(plan("2026-01-31"), d("2026-01-01"), d("2026-04-30")),
        )
    }

    @Test
    fun `vierteljaehrlich und jaehrlich`() {
        assertEquals(
            listOf(d("2026-01-15"), d("2026-04-15"), d("2026-07-15"), d("2026-10-15")),
            RecurringSchedule.dueDates(plan("2026-01-15", 3), d("2026-01-01"), d("2026-12-31")),
        )
        assertEquals(
            listOf(d("2027-03-01"), d("2028-03-01")),
            RecurringSchedule.dueDates(plan("2025-03-01", 12), d("2026-06-01"), d("2028-12-31")),
        )
    }

    @Test
    fun `schaltjahr 29 februar jaehrlich`() {
        assertEquals(
            listOf(d("2028-02-29"), d("2029-02-28"), d("2030-02-28"), d("2031-02-28"), d("2032-02-29")),
            RecurringSchedule.dueDates(plan("2028-02-29", 12), d("2028-01-01"), d("2032-12-31")),
        )
    }

    @Test
    fun `enddatum und zeitraum vor anker`() {
        assertEquals(
            listOf(d("2026-01-10"), d("2026-02-10")),
            RecurringSchedule.dueDates(plan("2026-01-10", end = "2026-02-20"), d("2025-01-01"), d("2026-12-31")),
        )
        assertTrue(RecurringSchedule.dueDates(plan("2026-01-10"), d("2026-03-01"), d("2026-02-01")).isEmpty())
    }

    @Test
    fun `weit entfernter zeitraum startet nah statt ab anker zu iterieren`() {
        val dates = RecurringSchedule.dueDates(plan("1990-05-03"), d("2026-10-01"), d("2026-11-30"))
        assertEquals(listOf(d("2026-10-03"), d("2026-11-03")), dates)
    }

    @Test
    fun `zustaende offen ueberfaellig gebucht uebersprungen`() {
        val p = plan("2026-08-01")
        val bookings = listOf(
            RecurringBooking(p.id, d("2026-08-01"), RecurringStatus.BOOKED, id(50)),
            RecurringBooking(p.id, d("2026-09-01"), RecurringStatus.SKIPPED, null),
        )
        val occ = RecurringSchedule.occurrences(p, bookings, d("2026-08-01"), d("2026-11-30"), today = d("2026-10-20"))
        assertEquals(
            listOf(OccurrenceState.BOOKED, OccurrenceState.SKIPPED, OccurrenceState.OVERDUE, OccurrenceState.OPEN),
            occ.map { it.state },
        )
        val pending = RecurringSchedule.pending(listOf(p), bookings, today = d("2026-10-20"), until = d("2026-11-19"))
        // Gebuchte und übersprungene Fälligkeiten tauchen nicht mehr als offen auf.
        assertEquals(listOf(d("2026-10-01"), d("2026-11-01")), pending.map { it.dueDate })
    }

    @Test
    fun `pending enthaelt ueberfaellige und kommende im horizont`() {
        val p = plan("2026-08-01")
        val pending = RecurringSchedule.pending(listOf(p), emptyList(), today = d("2026-10-20"), until = d("2026-11-19"), lookBackMonths = 1)
        assertEquals(listOf(d("2026-10-01"), d("2026-11-01")), pending.map { it.dueDate })
        assertEquals(listOf(OccurrenceState.OVERDUE, OccurrenceState.OPEN), pending.map { it.state })
    }

    @Test
    fun `inaktiver plan erzeugt keine offenen faelligkeiten`() {
        val p = plan("2026-08-01", active = false)
        assertTrue(RecurringSchedule.pending(listOf(p), emptyList(), d("2026-10-20"), d("2026-12-31")).isEmpty())
    }

    @Test
    fun `naechste faelligkeit und monatsaequivalent`() {
        assertEquals(d("2027-01-15"), RecurringSchedule.nextDue(plan("2026-01-15", 3), d("2026-10-16")))
        assertNull(RecurringSchedule.nextDue(plan("2026-01-15", end = "2026-03-01"), d("2026-10-16")))
        assertEquals(31_666L, RecurringSchedule.monthlyEquivalentMinor(plan("2026-01-15", 3)))
    }

    @Test
    fun `plan erlaubt nur einnahmen oder ausgaben`() {
        assertThrows(IllegalArgumentException::class.java) { plan("2026-01-01", kind = TransactionKind.TRANSFER) }
    }
}
