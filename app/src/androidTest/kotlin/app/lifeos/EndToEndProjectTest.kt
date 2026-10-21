package app.lifeos

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
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
 * Auftrag Teil 15: „Eine Buchung wird einem Projekt zugeordnet und erscheint dort korrekt" und
 * „Eine Buchung wird korrigiert und sämtliche zugehörigen Auswertungen aktualisieren sich".
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EndToEndProjectTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = false, timeout: Long = 15_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun typeAmount(text: String) {
        text.forEach { c -> rule.onNodeWithContentDescription(if (c == ',') "Komma" else c.toString()).performClick() }
    }

    @Test
    fun buchungZuordnenUndKorrigieren() {
        waitForText("HEUTE", timeout = 30_000)
        val name = "Samurai " + (System.currentTimeMillis() % 10_000)

        // ── Projekt anlegen ──
        rule.onNode(navTab("PROJEKTE")).performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Neues Projekt")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Neues Projekt").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(name)
        rule.onNodeWithText("Fahrzeug").performClick()
        Shots.take("20_projekt_editor")
        rule.onNodeWithText("SPEICHERN").performClick()
        waitForText("Projektverlauf", substring = true, timeout = 20_000)

        // ── Ausgabe erfassen (Haushaltskasse) ──
        rule.onNodeWithContentDescription("Zurück").performClick()
        rule.onNode(navTab("HOME")).performClick()
        waitForText("HEUTE")
        rule.onNodeWithContentDescription("Schnellerfassung").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Komma")).fetchSemanticsNodes().isNotEmpty() }
        typeAmount("89,9")
        rule.onNodeWithText("Lebensmittel").performClick()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Bremsbeläge")
        rule.onNodeWithText("SPEICHERN").performClick()
        waitForText("89,90", substring = true, timeout = 20_000)

        // ── Zuordnen ──
        rule.onNode(navTab("PROJEKTE")).performClick()
        waitForText(name)
        rule.onNodeWithText(name).performClick()
        waitForText("Buchung zuordnen")
        rule.onAllNodesWithText("Buchung zuordnen").onFirst().performClick()
        waitForText("Bremsbeläge")
        Shots.take("21_buchung_zuordnen")
        rule.onAllNodesWithText("Bremsbeläge").onLast().performClick()
        waitForText("1 verknüpfte Buchung", substring = true, timeout = 20_000)
        Shots.take("22_projekt_mit_kosten")

        // ── Korrigieren: aus dem Projekt heraus die Buchung öffnen und Betrag ändern ──
        rule.onNodeWithTag("lifeos-screen").performScrollToNode(hasText("Zugeordnete Buchungen", substring = true, ignoreCase = true))
        rule.onAllNodesWithText("Bremsbeläge").onLast().performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Betrag", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Betrag", substring = true).performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Löschen")).fetchSemanticsNodes().isNotEmpty() }
        repeat(6) { rule.onNodeWithContentDescription("Löschen").performClick() }
        typeAmount("99,9")
        rule.onNodeWithText("SPEICHERN").performClick()
        // Zurück in der Projektansicht: Kosten folgen der korrigierten Buchung
        waitForText("99,90", substring = true, timeout = 20_000)
        Shots.take("23_projekt_nach_korrektur")
        check(rule.onAllNodesWithText("89,90", substring = true).fetchSemanticsNodes().isEmpty()) { "Alter Betrag darf nirgends mehr erscheinen" }
    }
}
