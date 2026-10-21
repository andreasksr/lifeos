package app.lifeos

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.and
import androidx.compose.ui.test.hasText

/** Eintrag der Hauptnavigation – eindeutig, auch wenn derselbe Text als Überschrift vorkommt (z. B. „PLANUNG" auf Home). */
fun navTab(label: String): SemanticsMatcher = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
