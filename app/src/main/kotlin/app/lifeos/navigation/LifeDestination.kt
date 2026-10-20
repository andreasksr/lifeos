package app.lifeos.navigation

import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.feature.ledger.LedgerRoutes

/**
 * Zentrale Liste aller Hauptbereiche von LifeOS. Neue Module werden hier registriert und erst
 * sichtbar, wenn `implemented = true` – so gibt es nie funktionslose Navigationseinträge.
 */
enum class LifeDestination(
    val route: String,
    val label: String,
    val shape: NodeShape,
    val implemented: Boolean,
    val phase: Int,
) {
    TODAY("today", "Heute", NodeShape.DOT, implemented = false, phase = 5),
    LEDGER(LedgerRoutes.OVERVIEW, "Finanzen", NodeShape.DIAMOND, implemented = true, phase = 1),
    PROJECTS("projects", "Projekte", NodeShape.LINKED, implemented = false, phase = 4),
    ASSETS("assets", "Besitz", NodeShape.RING, implemented = false, phase = 4),
    LEARNING("learning", "Lernen", NodeShape.DOT, implemented = false, phase = 6),
    ARCHIVE("archive", "Archiv", NodeShape.RING, implemented = false, phase = 5),
    SYSTEM("system", "System", NodeShape.RING, implemented = true, phase = 1);

    companion object {
        val visible: List<LifeDestination> get() = entries.filter { it.implemented }
        fun forRoute(route: String?): LifeDestination? = entries.firstOrNull { it.route == route }
    }
}
