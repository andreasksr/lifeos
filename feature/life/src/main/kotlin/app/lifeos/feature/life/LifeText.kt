package app.lifeos.feature.life

import app.lifeos.core.domain.life.LifeError
import app.lifeos.core.domain.life.VehicleText
import app.lifeos.core.domain.life.WarrantyState
import app.lifeos.core.model.ExamPart
import app.lifeos.core.model.TopicStatus
import app.lifeos.core.model.VehicleEventKind

/** Texte für „Mein Leben". Android-frei, damit sie mit JVM-Tests geprüft werden können. */
object LifeText {
    fun error(e: LifeError): String = when (e) {
        LifeError.NameRequired -> "Bitte einen Namen eingeben."
        LifeError.NameTooLong -> "Der Name ist zu lang (max. 120 Zeichen)."
        LifeError.InvalidYear -> "Das Baujahr ist nicht plausibel."
        LifeError.InvalidOdometer -> "Der Kilometerstand ist nicht plausibel."
        LifeError.InvalidAmount -> "Der Betrag darf nicht negativ sein."
        LifeError.NotFound -> "Der Eintrag existiert nicht mehr."
        LifeError.EndBeforeStart -> "Das Ende muss nach dem Beginn liegen."
        LifeError.SessionTooLong -> "Eine Lerneinheit darf höchstens 12 Stunden dauern."
        LifeError.SessionOverlaps -> "Überschneidet sich mit bereits erfasster Lernzeit – sie würde sonst doppelt zählen."
        LifeError.SessionRunning -> "Es läuft bereits eine Lernzeit. Bitte zuerst stoppen."
        LifeError.NoRunningSession -> "Es läuft keine Lernzeit."
        LifeError.SessionTooShort -> "Sofort gestoppt – unter einer Sekunde wird nichts gespeichert."
        LifeError.InFuture -> "Lernzeit kann nicht in der Zukunft liegen."
        LifeError.InvalidDate -> "Das Garantieende liegt vor dem Kaufdatum."
    }

    fun eventKind(k: VehicleEventKind): String = VehicleText.kind(k)

    fun warranty(w: WarrantyState): String = when (w) {
        WarrantyState.NONE -> "Keine Garantie erfasst"
        WarrantyState.ACTIVE -> "Garantie aktiv"
        WarrantyState.EXPIRING -> "Garantie läuft bald ab"
        WarrantyState.EXPIRED -> "Garantie abgelaufen"
    }

    fun part(p: ExamPart): String = when (p) {
        ExamPart.BQ -> "BQ"
        ExamPart.HQ -> "HQ"
        ExamPart.OTHER -> "Sonstiges"
    }

    fun topicStatus(s: TopicStatus): String = when (s) {
        TopicStatus.OPEN -> "Offen"
        TopicStatus.IN_PROGRESS -> "In Arbeit"
        TopicStatus.DONE -> "Sicher"
    }

    /** „1,2 MB", „840 KB", „12 B". */
    fun fileSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> {
            val tenths = bytes * 10 / (1024 * 1024)
            "${tenths / 10},${tenths % 10} MB"
        }
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    /** Laufzeit eines Timers: „0:07:05". */
    fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "${s / 3600}:${((s % 3600) / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** Ganze Zahl aus Eingabetext („150.000" → 150000); leer → null; ungültig → Fehler. */
    fun parseInt(text: String): Result<Int?> {
        val t = text.trim().replace(".", "").replace(" ", "")
        if (t.isEmpty()) return Result.success(null)
        return t.toIntOrNull()?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException(text))
    }

    fun km(km: Int): String {
        val s = km.toString()
        return buildString {
            s.forEachIndexed { i, c ->
                if (i > 0 && (s.length - i) % 3 == 0) append('.')
                append(c)
            }
        } + " km"
    }
}
