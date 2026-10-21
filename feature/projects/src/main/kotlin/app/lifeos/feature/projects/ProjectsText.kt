package app.lifeos.feature.projects

import app.lifeos.core.domain.projects.ProjectError
import app.lifeos.core.model.ProgressMode
import app.lifeos.core.model.ProjectStatus

object ProjectsText {
    fun status(s: ProjectStatus): String = when (s) {
        ProjectStatus.PLANNED -> "Geplant"
        ProjectStatus.ACTIVE -> "Aktiv"
        ProjectStatus.PAUSED -> "Pausiert"
        ProjectStatus.DONE -> "Abgeschlossen"
        ProjectStatus.CANCELLED -> "Abgebrochen"
    }

    fun mode(m: ProgressMode): String = when (m) {
        ProgressMode.TASKS -> "Aufgaben"
        ProgressMode.MILESTONES -> "Meilensteine"
        ProgressMode.MANUAL -> "Manuell"
    }

    fun error(e: ProjectError): String = when (e) {
        ProjectError.NameRequired -> "Bitte einen Namen eingeben."
        ProjectError.NameTooLong -> "Der Name ist zu lang (max. 120 Zeichen)."
        ProjectError.TargetBeforeStart -> "Der Zieltermin liegt vor dem Start."
        ProjectError.ManualProgressMissing -> "Bitte den Fortschritt festlegen."
        ProjectError.NotFound -> "Das Projekt existiert nicht mehr."
        ProjectError.NotATransaction -> "Die Buchung existiert nicht mehr."
        ProjectError.AlreadyLinked -> "Diese Buchung ist dem Projekt bereits zugeordnet."
        ProjectError.InvalidBudget -> "Das Budget muss größer als 0 sein."
    }

    /** Vorschläge für Projektkategorien – frei erweiterbar, keine feste Liste. */
    val suggestedCategories = listOf("Fahrzeug", "Haus & Garten", "Werkstatt", "Elektronik", "Weiterbildung", "Freizeit")
}
