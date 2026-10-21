package app.lifeos.feature.life.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing

/** Einrückung an der Lebenslinie für Formularfelder. */
val SpineInset: Modifier = Modifier.padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter)

/** Bildschirm für Formulare: Kopf, scrollbarer Inhalt, Speichern-Leiste in Daumenreichweite. */
@Composable
fun EditorScaffold(
    overline: String,
    title: String,
    onBack: () -> Unit,
    saving: Boolean,
    onSave: () -> Unit,
    generalError: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Tastatur beim Verlassen des Formulars schließen (sonst bleibt sie über der Detailansicht stehen).
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    Column(Modifier.fillMaxSize().imePadding()) {
        LifeTopBar(overline = overline, title = title, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("lifeos-editor"), verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.l)) {
            content()
            generalError?.let { Text(it, style = Continuum.type.body, color = ContinuumColors.Alert, modifier = SpineInset) }
            Spacer(Modifier.height(ContinuumSpacing.l))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = ContinuumSpacing.spineRail, end = ContinuumSpacing.gutter, top = 8.dp, bottom = 12.dp)) {
            LifeButton(if (saving) "Speichert …" else "Speichern", onSave, enabled = !saving, glyph = Glyph.CHECK, fill = true)
        }
    }
}

@Composable
fun FieldGroup(title: String, error: String? = null, content: @Composable () -> Unit) {
    Column(SpineInset, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = Continuum.type.label, color = if (error != null) ContinuumColors.Alert else ContinuumColors.Muted)
        content()
        if (error != null) Text(error, style = Continuum.type.small, color = ContinuumColors.Alert)
    }
}
