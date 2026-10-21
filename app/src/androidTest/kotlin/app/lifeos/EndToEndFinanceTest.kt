package app.lifeos

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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

    /** Die Buchungszeile (klickbar) – nicht die gleichnamige Kategoriezeile. */
    private val txLine = hasText("Lebensmittel") and hasClickAction() and hasText("Haushaltskasse", substring = true)

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
        Shots.take("02_schnellerfassung")
        rule.onNodeWithText("SPEICHERN").performClick()
        // Prüft den dauerhaften Zustand (Home-Kennzahl), nicht den 3-Sekunden-Hinweis –
        // auf einem langsamen Emulator ist der Hinweis evtl. schon wieder weg.
        waitForText("Heute ausgegeben: 12,50", substring = true, timeout = 20_000)
        Shots.take("03_home_nach_speichern")

        // ── Finanzen ──
        rule.onNodeWithText("FINANZEN").performClick()
        waitFor(hasContentDescription("Kontostand", substring = true))
        Shots.take("04_finanzen")
        scrollTo(txLine)
        Shots.take("05_finanzen_buchungen")

        // ── Bearbeiten: 12,50 € → 8,00 € ──
        rule.onAllNodes(txLine).onFirst().performClick()
        waitFor(hasContentDescription("Betrag", substring = true))
        rule.onNodeWithContentDescription("Betrag", substring = true).performClick()
        waitFor(hasContentDescription("Löschen"))
        repeat(5) { rule.onNodeWithContentDescription("Löschen").performClick() }
        typeAmount("8")
        Shots.take("06_buchung_bearbeiten")
        rule.onNodeWithText("SPEICHERN").performClick()
        waitForText("8,00", substring = true, timeout = 20_000)

        // ── Löschen mit Sicherheitsabfrage, dann Rückgängig ──
        scrollTo(txLine)
        rule.onAllNodes(txLine).onFirst().performClick()
        waitFor(hasContentDescription("Buchung löschen"))
        rule.onNodeWithContentDescription("Buchung löschen").performClick()
        waitForText("LÖSCHEN")
        Shots.take("07_loeschen_abfrage")
        rule.onNodeWithText("LÖSCHEN").performClick()
        waitForText("RÜCKGÄNGIG", timeout = 5_000)
        rule.onNodeWithText("RÜCKGÄNGIG").performClick()
        // Die Zeile kann unterhalb des sichtbaren Bereichs liegen (LazyColumn komponiert sie dann nicht):
        // bis zu 20 s lang wiederholt hinscrollen.
        rule.waitUntil(20_000) { runCatching { scrollTo(txLine) }.isSuccess && rule.onAllNodes(txLine).fetchSemanticsNodes().isNotEmpty() }
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
