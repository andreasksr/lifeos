package app.lifeos.core.domain.ledger

import app.lifeos.core.model.RecurringBooking
import app.lifeos.core.model.RecurringPayment
import app.lifeos.core.model.RecurringStatus
import java.time.LocalDate

enum class OccurrenceState {
    /** Noch nicht gebucht, Fälligkeit liegt in der Vergangenheit. */
    OVERDUE,

    /** Noch nicht gebucht, heute oder künftig fällig. */
    OPEN,
    BOOKED,
    SKIPPED,
}

data class Occurrence(
    val payment: RecurringPayment,
    val dueDate: LocalDate,
    val state: OccurrenceState,
    val booking: RecurringBooking?,
)

/**
 * Berechnet Fälligkeiten wiederkehrender Zahlungen – deterministisch und ohne zu buchen.
 *
 * Termin k = anchorDate.plusMonths(k · intervalMonths). Da immer vom Anker aus gerechnet wird,
 * „wandert" ein 31. nicht dauerhaft auf den 28. (31.01. → 28.02. → 31.03.).
 */
object RecurringSchedule {

    /** Hartes Limit gegen Endlosschleifen bei absurden Zeiträumen. */
    private const val MAX_OCCURRENCES = 2_000

    fun dueDates(payment: RecurringPayment, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        require(payment.intervalMonths > 0) { "Intervall muss > 0 sein" }
        val result = mutableListOf<LocalDate>()
        // Erstes k so wählen, dass wir nahe an `from` starten (vermeidet Iteration ab Anker).
        val monthsToFrom = java.time.temporal.ChronoUnit.MONTHS.between(
            payment.anchorDate.withDayOfMonth(1), from.withDayOfMonth(1),
        )
        var k = maxOf(0L, monthsToFrom / payment.intervalMonths - 1)
        var guard = 0
        while (guard++ < MAX_OCCURRENCES) {
            val date = payment.anchorDate.plusMonths(k * payment.intervalMonths)
            if (payment.endDate?.let { date.isAfter(it) } == true) break
            if (date.isAfter(to)) break
            if (!date.isBefore(from)) result += date
            k++
        }
        return result
    }

    /**
     * Alle Fälligkeiten im Zeitraum mit ihrem Zustand. Inaktive Pläne liefern nur bereits
     * gebuchte/übersprungene Fälligkeiten (Historie bleibt sichtbar), aber keine offenen.
     */
    fun occurrences(
        payment: RecurringPayment,
        bookings: List<RecurringBooking>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
    ): List<Occurrence> {
        val mine = bookings.filter { it.recurringId == payment.id }.associateBy { it.dueDate }
        return dueDates(payment, from, to).mapNotNull { due ->
            val booking = mine[due]
            val state = when {
                booking?.status == RecurringStatus.BOOKED -> OccurrenceState.BOOKED
                booking?.status == RecurringStatus.SKIPPED -> OccurrenceState.SKIPPED
                !payment.active -> return@mapNotNull null
                due.isBefore(today) -> OccurrenceState.OVERDUE
                else -> OccurrenceState.OPEN
            }
            Occurrence(payment, due, state, booking)
        }
    }

    /** Offene und überfällige Fälligkeiten aller Pläne bis [until], sortiert nach Datum. */
    fun pending(
        payments: List<RecurringPayment>,
        bookings: List<RecurringBooking>,
        today: LocalDate,
        until: LocalDate,
        lookBackMonths: Long = 3,
    ): List<Occurrence> = payments
        .filter { it.active }
        .flatMap { occurrences(it, bookings, today.minusMonths(lookBackMonths), until, today) }
        .filter { it.state == OccurrenceState.OPEN || it.state == OccurrenceState.OVERDUE }
        .sortedWith(compareBy<Occurrence> { it.dueDate }.thenBy { it.payment.name })

    /** Nächste Fälligkeit ab [from] (inkl.), unabhängig vom Buchungsstatus. */
    fun nextDue(payment: RecurringPayment, from: LocalDate): LocalDate? =
        dueDates(payment, from, from.plusMonths(payment.intervalMonths.toLong() + 1)).firstOrNull()

    /** Durchschnittlicher Monatsbetrag (abgerundet) – für „Fixkosten pro Monat". */
    fun monthlyEquivalentMinor(payment: RecurringPayment): Long = payment.amount.minor / payment.intervalMonths

    fun intervalLabel(months: Int): String = when (months) {
        1 -> "monatlich"
        2 -> "alle 2 Monate"
        3 -> "vierteljährlich"
        6 -> "halbjährlich"
        12 -> "jährlich"
        else -> "alle $months Monate"
    }
}
