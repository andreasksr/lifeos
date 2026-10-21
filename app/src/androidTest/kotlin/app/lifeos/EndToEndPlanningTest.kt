package app.lifeos

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
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
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Planung über die echte Oberfläche: Aufgabe per Schnellerfassung → erscheint auf Home und in der
 * Planung → über den Knoten abhaken; Termin anlegen → erscheint in Tag/Woche/Monat. Mit Screenshots.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EndToEndPlanningTest {

    @get:Rule(order = 0)
    val notifications: GrantPermissionRule = if (android.os.Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = false, timeout: Long = 15_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun clickLabel(label: String) = SemanticsMatcher("onClick-Label = $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test
    fun aufgabeErfassenAbhakenUndTerminPlanen() {
        waitForText("HEUTE", timeout = 30_000)

        // ── Aufgabe per zentraler Schnellerfassung ──
        rule.onNodeWithContentDescription("Schnellerfassung").performClick()
        waitForText("Aufgabe")
        rule.onAllNodesWithText("Aufgabe").onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Ölwechsel Samurai")
        Shots.take("10_schnellerfassung_aufgabe")
        rule.onNodeWithText("AUFGABE SPEICHERN").performClick()
        waitForText("Ölwechsel Samurai", timeout = 20_000)
        Shots.take("11_home_mit_aufgabe")

        // ── Planung: Aufgabe sichtbar, Knoten hakt ab ──
        rule.onNode(navTab("PLANUNG")).performClick()
        waitForText("Ölwechsel Samurai")
        Shots.take("12_planung_tag")
        rule.onNode(clickLabel("„Ölwechsel Samurai“ erledigen")).performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(clickLabel("„Ölwechsel Samurai“ wieder öffnen")).fetchSemanticsNodes().isNotEmpty() }

        // ── Termin anlegen ──
        rule.onNodeWithContentDescription("Neuer Termin").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Unterricht Mechatronik")
        rule.onNodeWithText("Wöchentlich").performClick()
        rule.onNodeWithTag("lifeos-editor").performScrollToNode(hasText("1 Std vorher"))
        rule.onNodeWithText("1 Std vorher").performClick()
        Shots.take("13_termin_editor")
        rule.onNodeWithText("SPEICHERN").performClick()
        waitForText("Unterricht Mechatronik", timeout = 20_000)

        rule.onNodeWithText("WOCHE").performClick()
        waitForText("Unterricht Mechatronik")
        Shots.take("14_planung_woche")
        rule.onNodeWithText("MONAT").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Einträge", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        Shots.take("15_planung_monat")
        rule.onNodeWithText("AUFGABEN").performClick()
        waitForText("Erledigte anzeigen", substring = true)
        Shots.take("16_planung_aufgaben")
    }
}
