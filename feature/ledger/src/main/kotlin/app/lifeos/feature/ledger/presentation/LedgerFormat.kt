package app.lifeos.feature.ledger.presentation

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Deutsche Texte für Datum und Anteile – bewusst ohne Android/ICU, damit die Ausgabe auf jedem
 * Gerät identisch und in JVM-Tests prüfbar ist.
 */
object LedgerFormat {

    private val MONTHS = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )
    private val MONTHS_SHORT = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.")

    fun monthTitle(month: YearMonth): String = "${MONTHS[month.monthValue - 1]} ${month.year}"

    fun monthName(month: YearMonth): String = MONTHS[month.monthValue - 1]

    fun weekday(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Mo"
        DayOfWeek.TUESDAY -> "Di"
        DayOfWeek.WEDNESDAY -> "Mi"
        DayOfWeek.THURSDAY -> "Do"
        DayOfWeek.FRIDAY -> "Fr"
        DayOfWeek.SATURDAY -> "Sa"
        DayOfWeek.SUNDAY -> "So"
    }

    /** „Heute", „Gestern", „Morgen" oder „Di, 14. Okt." (mit Jahr, wenn abweichend). */
    fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        today.plusDays(1) -> "Morgen"
        else -> buildString {
            append(weekday(date.dayOfWeek)).append(", ").append(date.dayOfMonth).append(". ")
            append(MONTHS_SHORT[date.monthValue - 1])
            if (date.year != today.year) append(' ').append(date.year)
        }
    }

    /** Vollständiges Datum für Formulare: „20.10.2026". */
    fun numericDate(date: LocalDate): String =
        "${date.dayOfMonth.pad(2)}.${date.monthValue.pad(2)}.${date.year.pad(4)}"

    private fun Int.pad(width: Int) = toString().padStart(width, '0')

    /** Promille → „113 %" (abgerundet, ganzzahlig – keine Scheingenauigkeit). */
    fun percent(permille: Long): String = "${permille / 10} %"
}
