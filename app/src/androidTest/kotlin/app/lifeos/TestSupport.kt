package app.lifeos

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText

/** Eintrag der Hauptnavigation – eindeutig, auch wenn derselbe Text als Überschrift vorkommt (z. B. „PLANUNG" auf Home). */
fun navTab(label: String): SemanticsMatcher = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

/**
 * Bei einem Fehlschlag: Bildschirmfoto + Semantikbaum nach `Download/lifeos-shots/FAIL_<Test>.*` –
 * so ist in der CI sichtbar, WAS auf dem Bildschirm war.
 */
class FailureCapture(private val tree: () -> String) : org.junit.rules.TestWatcher() {
    override fun failed(e: Throwable?, description: org.junit.runner.Description) {
        val name = "FAIL_" + description.methodName
        runCatching { Shots.take(name) }
        runCatching { Shots.text(name, (e?.toString() ?: "") + "\n\n" + tree()) }
    }
}
