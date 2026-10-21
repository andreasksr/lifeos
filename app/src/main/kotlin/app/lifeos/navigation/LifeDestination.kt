package app.lifeos.navigation

import app.lifeos.core.designsystem.component.Glyph

/**
 * Die fünf Hauptbereiche von LifeOS (Auftrag Teil 11). Ein Bereich erscheint erst in der Navigation,
 * wenn er `implemented = true` ist – es gibt nie funktionslose Navigationseinträge.
 */
enum class LifeDestination(
    val route: String,
    val label: String,
    val glyph: Glyph,
    val implemented: Boolean,
) {
    HOME("home", "Home", Glyph.HOME, implemented = true),
    FINANCE("finance", "Finanzen", Glyph.WALLET, implemented = true),
    PLANNING("planning", "Planung", Glyph.TASKS, implemented = true),
    PROJECTS("projects", "Projekte", Glyph.PROJECT, implemented = false),
    LIFE("life", "Mein Leben", Glyph.LIFE, implemented = true);

    companion object {
        val visible: List<LifeDestination> get() = entries.filter { it.implemented }
        fun forRoute(route: String?): LifeDestination? = entries.firstOrNull { it.route == route }
    }
}
