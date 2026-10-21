package app.lifeos.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Deutsche Texte für Datum, Fälligkeiten und Anteile – app-weit einheitlich – bewusst ohne Android/ICU, damit die Ausgabe auf jedem
 * Gerät identisch und in JVM-Tests prüfbar ist.
 */
object LifeFormat {

    private val MONTHS = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )
    private val MONTHS_SHORT = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.")

    fun monthTitle(month: YearMonth): String = "${MONTHS[month.monthValue - 1]} ${month.year}"

    fun monthName(month: YearMonth): String = MONTHS[month.monthValue - 1]

    /** Drei-Zeichen-Kürzel für Diagrammachsen: „Okt", „Mär". */
    fun monthAxis(month: YearMonth): String = MONTHS[month.monthValue - 1].take(3)

    fun kindLabel(kind: TransactionKind): String = when (kind) {
        TransactionKind.EXPENSE -> "Ausgabe"
        TransactionKind.INCOME -> "Einnahme"
        TransactionKind.TRANSFER -> "Umbuchung"
        TransactionKind.REFUND -> "Erstattung"
        TransactionKind.CORRECTION_IN, TransactionKind.CORRECTION_OUT -> "Korrektur"
    }

    fun accountKindLabel(kind: AccountKind): String = when (kind) {
        AccountKind.CASH -> "Bargeld"
        AccountKind.CHECKING -> "Girokonto"
        AccountKind.SAVINGS -> "Sparkonto"
        AccountKind.CREDIT_CARD -> "Kreditkarte"
    }

    /** „in 3 Tagen", „heute", „seit 2 Tagen überfällig". */
    fun relativeDue(due: LocalDate, today: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
        return when {
            days == 0L -> "heute fällig"
            days == 1L -> "morgen fällig"
            days > 1L -> "in $days Tagen fällig"
            days == -1L -> "seit gestern überfällig"
            else -> "seit ${-days} Tagen überfällig"
        }
    }

    fun greeting(hour: Int): String = when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Guten Tag"
        in 18..22 -> "Guten Abend"
        else -> "Gute Nacht"
    }

    fun weekdayLong(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Montag"
        DayOfWeek.TUESDAY -> "Dienstag"
        DayOfWeek.WEDNESDAY -> "Mittwoch"
        DayOfWeek.THURSDAY -> "Donnerstag"
        DayOfWeek.FRIDAY -> "Freitag"
        DayOfWeek.SATURDAY -> "Samstag"
        DayOfWeek.SUNDAY -> "Sonntag"
    }

    /** „21. Oktober 2026". */
    fun longDate(date: LocalDate): String = "${date.dayOfMonth}. ${MONTHS[date.monthValue - 1]} ${date.year}"

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

    fun typeLabel(type: NodeType): String = when (type) {
        NodeType.PROJECT -> "Projekt"
        NodeType.VEHICLE -> "Fahrzeug"
        NodeType.ASSET -> "Inventar"
        NodeType.DOCUMENT -> "Dokument"
        NodeType.TASK -> "Aufgabe"
        NodeType.EVENT -> "Termin"
        NodeType.TRANSACTION -> "Buchung"
        NodeType.VEHICLE_EVENT -> "Fahrzeughistorie"
        NodeType.COURSE -> "Fach"
        NodeType.TOPIC -> "Thema"
        NodeType.EXAM -> "Prüfung"
        NodeType.MILESTONE -> "Meilenstein"
        NodeType.RECURRING -> "Wiederkehrende Zahlung"
        NodeType.ACCOUNT -> "Konto"
        NodeType.CATEGORY -> "Kategorie"
        NodeType.CONTRACT -> "Vertrag"
        NodeType.NOTE -> "Notiz"
    }

    /** „09:05" – unabhängig von der Gerätesprache. */
    fun time(t: java.time.LocalTime): String = "${t.hour.pad(2)}:${t.minute.pad(2)}"

    /** „1 Std 25 Min", „45 Min", „0 Min". */
    fun duration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h Std $m Min"
            h > 0 -> "$h Std"
            else -> "$m Min"
        }
    }
}
