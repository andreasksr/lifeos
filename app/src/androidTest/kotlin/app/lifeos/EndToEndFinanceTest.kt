package app.lifeos

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ende-zu-Ende über die echte Oberfläche, echte (verschlüsselte) Datenbank, echter Emulator:
 * Ausgabe anlegen → bearbeiten → löschen → rückgängig. Nebenbei entstehen die Screenshots für die Doku.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EndToEndFinanceTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = false, timeout: Long = 10_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 10_000) {
        rule.waitUntil(timeout) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        rule.onNodeWithTag("lifeos-screen").performScrollToNode(matcher)
        rule.waitForIdle()
    }

    private fun typeAmount(text: String) {
        text.forEach { c ->
            val key = if (c == ',') "Komma" else c.toString()
            rule.onNodeWithContentDescription(key).performClick()
        }
    }

    @Test
    fun ausgabeAnlegenBearbeitenLoeschenUndRueckgaengig() {
        // Start: Home ist da (DB wird verschlüsselt geöffnet, Standardkategorien angelegt)
        waitForText("HEUTE", timeout = 30_000)
        Shots.take("01_home")

        // ── Anlegen über die zentrale Schnellerfassung ──
        rule.onNodeWithContentDescription("Schnellerfassung").performClick()
        waitFor(hasContentDescription("Komma"))
        typeAmount("12,5")
        rule.onNodeWithText("Lebensmittel").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Wochenmarkt")
        Shots.take("02_schnellerfassung")
        rule.onNodeWithText("SPEICHERN").performClick()
        waitForText("Ausgabe gespeichert", substring = true)
        Shots.take("03_home_nach_speichern")

        // ── Finanzen ──
        rule.onNodeWithText("FINANZEN").performClick()
        waitFor(hasContentDescription("Kontostand", substring = true))
        Shots.take("04_finanzen")
        scrollTo(hasText("Wochenmarkt"))
        Shots.take("05_finanzen_buchungen")

        // ── Bearbeiten: 12,50 € → 8,00 € ──
        rule.onAllNodesWithText("Wochenmarkt").onFirst().performClick()
        waitFor(hasContentDescription("Betrag", substring = true))
        rule.onNodeWithContentDescription("Betrag", substring = true).performClick()
        waitFor(hasContentDescription("Löschen"))
        repeat(5) { rule.onNodeWithContentDescription("Löschen").performClick() }
        typeAmount("8")
        Shots.take("06_buchung_bearbeiten")
        rule.onNodeWithText("SPEICHERN").performClick()
        waitForText("Ausgabe geändert", substring = true)

        // ── Löschen mit Sicherheitsabfrage, dann Rückgängig ──
        scrollTo(hasText("Wochenmarkt"))
        rule.onAllNodesWithText("Wochenmarkt").onFirst().performClick()
        waitFor(hasContentDescription("Buchung löschen"))
        rule.onNodeWithContentDescription("Buchung löschen").performClick()
        waitForText("LÖSCHEN")
        Shots.take("07_loeschen_abfrage")
        rule.onNodeWithText("LÖSCHEN").performClick()
        waitForText("Buchung gelöscht")
        rule.onNodeWithText("RÜCKGÄNGIG").performClick()
        waitForText("Buchung wiederhergestellt")
        scrollTo(hasText("Wochenmarkt"))
        rule.onAllNodesWithText("8,00", substring = true).fetchSemanticsNodes().let {
            check(it.isNotEmpty()) { "Wiederhergestellte Buchung (8,00 €) nicht sichtbar" }
        }

        // ── Weitere Ansichten für die Dokumentation ──
        rule.onNodeWithText("MEIN LEBEN").performClick()
        waitForText("System & Sicherheit")
        Shots.take("08_mein_leben")
        rule.onNodeWithText("HOME").performClick()
        waitForText("HEUTE")
        Shots.take("09_home_mit_daten")
    }
}
