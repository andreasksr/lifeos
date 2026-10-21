package app.lifeos.feature.planning.presentation

import app.lifeos.core.domain.planning.PlanningError
import app.lifeos.core.model.LifeFormat
import app.lifeos.core.model.Priority
import app.lifeos.core.model.Repeat
import app.lifeos.core.model.RepeatUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Deutsche Texte und Auswahloptionen der Planung – Android-frei, damit testbar. */
object PlanningText {

    fun priority(p: Priority): String = when (p) {
        Priority.LOW -> "Niedrig"
        Priority.NORMAL -> "Normal"
        Priority.HIGH -> "Hoch"
    }

    /** „Täglich", „Alle 2 Wochen", „Jährlich". */
    fun repeat(r: Repeat?): String {
        if (r == null) return "Keine"
        if (r.interval == 1) {
            return when (r.unit) {
                RepeatUnit.DAY -> "Täglich"
                RepeatUnit.WEEK -> "Wöchentlich"
                RepeatUnit.MONTH -> "Monatlich"
                RepeatUnit.YEAR -> "Jährlich"
            }
        }
        val unit = when (r.unit) {
            RepeatUnit.DAY -> "Tage"
            RepeatUnit.WEEK -> "Wochen"
            RepeatUnit.MONTH -> "Monate"
            RepeatUnit.YEAR -> "Jahre"
        }
        return "Alle ${r.interval} $unit"
    }

    fun unitShort(u: RepeatUnit): String = when (u) {
        RepeatUnit.DAY -> "Tag"
        RepeatUnit.WEEK -> "Woche"
        RepeatUnit.MONTH -> "Monat"
        RepeatUnit.YEAR -> "Jahr"
    }

    /** Erinnerungsoptionen (Minuten vor Fälligkeit/Beginn). `null` = keine. */
    val reminderOptions: List<Int?> = listOf(null, 0, 15, 60, 24 * 60)

    fun reminder(minutes: Int?): String = when (minutes) {
        null -> "Keine"
        0 -> "Zum Zeitpunkt"
        in 1..59 -> "$minutes Min vorher"
        60 -> "1 Std vorher"
        in 61..(24 * 60 - 1) -> "${minutes / 60} Std vorher"
        24 * 60 -> "1 Tag vorher"
        else -> "${minutes / (24 * 60)} Tage vorher"
    }

    fun error(e: PlanningError): String = when (e) {
        PlanningError.TitleRequired -> "Bitte einen Titel eingeben."
        PlanningError.TitleTooLong -> "Der Titel ist zu lang (max. 200 Zeichen)."
        PlanningError.RepeatNeedsDueDate -> "Eine Wiederholung braucht ein Fälligkeitsdatum."
        PlanningError.EndBeforeStart -> "Das Ende liegt vor dem Beginn."
        PlanningError.NotFound -> "Der Eintrag existiert nicht mehr."
        PlanningError.InvalidReminder -> "Diese Erinnerung ist nicht möglich."
    }

    /** „Heute · 09:00", „Mi, 22. Okt.", „Ohne Datum". */
    fun due(date: LocalDate?, time: LocalTime?, today: LocalDate): String {
        if (date == null) return "Ohne Datum"
        return LifeFormat.dayTitle(date, today) + (time?.let { " · " + LifeFormat.time(it) } ?: "")
    }

    /** „09:00–10:30", „ganztägig", mehrtägig „bis Fr, 24. Okt.". */
    fun eventSpan(start: LocalDateTime, end: LocalDateTime, allDay: Boolean, today: LocalDate): String {
        val multiDay = end.toLocalDate().isAfter(start.toLocalDate())
        return when {
            allDay && !multiDay -> "ganztägig"
            allDay -> "ganztägig bis ${LifeFormat.dayTitle(end.toLocalDate(), today)}"
            multiDay -> "${LifeFormat.time(start.toLocalTime())} bis ${LifeFormat.dayTitle(end.toLocalDate(), today)} ${LifeFormat.time(end.toLocalTime())}"
            else -> "${LifeFormat.time(start.toLocalTime())}–${LifeFormat.time(end.toLocalTime())}"
        }
    }
}
