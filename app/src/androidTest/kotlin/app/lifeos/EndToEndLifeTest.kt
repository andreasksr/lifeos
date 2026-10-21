package app.lifeos

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
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
 * „Mein Leben" über die echte Oberfläche: Fahrzeug anlegen, Hauptuntersuchung planen; Industriemeister:
 * Zielzeiträume (nicht bestätigt), Lernzeit starten/stoppen, Nachtrag mit Überschneidung wird abgewiesen.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EndToEndLifeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 10)
    val failure = FailureCapture { rule.onRoot(useUnmergedTree = true).printToString(maxDepth = 60) }

    private fun waitForText(text: String, substring: Boolean = false, timeout: Long = 15_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun back() {
        rule.onNodeWithContentDescription("Zurück").performClick()
    }

    @Test
    fun fahrzeugUndWeiterbildung() {
        waitForText("HEUTE", timeout = 30_000)
        val suffix = (System.currentTimeMillis() % 10_000).toString()

        // ── Mein Leben → Fahrzeuge ──
        rule.onNode(navTab("MEIN LEBEN")).performClick()
        waitForText("Fahrzeuge")
        Shots.take("30_mein_leben")
        rule.onNodeWithText("Fahrzeuge").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Neues Fahrzeug")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Neues Fahrzeug").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 6 }
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Suzuki")
        fields[1].performTextInput("Samurai $suffix")
        fields[3].performTextInput("1993")
        Shots.take("31_fahrzeug_editor")
        rule.onNodeWithText("SPEICHERN").performClick()
        waitForText("Fahrzeughistorie", substring = true, timeout = 20_000)
        waitForText("Baujahr 1993", substring = true)

        // ── Hauptuntersuchung planen ──
        rule.onNodeWithText("Planen  →").performClick()
        waitForText("Hauptuntersuchung")
        rule.onNodeWithText("SPEICHERN").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Hauptuntersuchung · ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        Shots.take("32_fahrzeug_detail")

        // ── Weiterbildung ──
        back()
        back()
        waitForText("Industriemeister")
        rule.onNodeWithText("Industriemeister").performClick()
        rule.onNodeWithTag("lifeos-screen").performScrollToNode(hasText("ZIELZEITRÄUME EINTRAGEN"))
        rule.onNodeWithText("ZIELZEITRÄUME EINTRAGEN").performClick()
        waitForText("Basisqualifikationen")
        // Zielzeitraum wird nie als bestätigter Termin gezeigt
        waitForText("Zielzeitraum Ende April 2027 · noch nicht bestätigt")
        waitForText("Zielzeitraum Mai 2028 · noch nicht bestätigt")

        rule.onNodeWithTag("lifeos-screen").performScrollToNode(hasText("STARTEN"))
        rule.onNodeWithText("STARTEN").performClick()
        waitForText("STOPPEN")
        Thread.sleep(1_500)
        Shots.take("33_lernzeit_laeuft")
        rule.onNodeWithText("STOPPEN").performClick()
        waitForText("STARTEN")

        // Nachtrag über die gerade gemessene Zeit → wird abgewiesen (keine Doppelzählung)
        rule.onNodeWithText("Zeit nachtragen  →").performClick()
        waitForText("NACHTRAGEN")
        rule.onNodeWithText("NACHTRAGEN").performClick()
        waitForText("Überschneidet sich", substring = true)
        Shots.take("34_nachtrag_ueberschneidung")
        rule.onAllNodesWithText("30 Min").onFirst().performClick()
        rule.onNodeWithText("NACHTRAGEN").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("NACHTRAGEN").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("lifeos-screen").performScrollToNode(hasText(" · nachgetragen", substring = true))
        Shots.take("35_weiterbildung")
    }
}
