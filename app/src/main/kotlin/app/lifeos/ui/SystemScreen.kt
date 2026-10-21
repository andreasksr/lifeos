package app.lifeos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.AppContainer
import app.lifeos.core.database.LifeOsDatabase
import app.lifeos.core.designsystem.component.Glyph
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeGlyph
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifeTimelineRow
import app.lifeos.core.designsystem.component.LifeToggle
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.component.NodeShape
import app.lifeos.core.designsystem.component.NodeTone
import app.lifeos.core.designsystem.component.SpineSection
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.domain.ports.IntegrityReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Ein Modul im Bereich „Mein Leben". */
data class LifeModule(val title: String, val subtitle: String, val glyph: Glyph, val open: () -> Unit)

/** „Mein Leben" – Einstieg in persönliche Verwaltung. Zeigt nur implementierte Module. */
@Composable
fun LifeHubScreen(onOpenSystem: () -> Unit, modules: List<LifeModule> = emptyList()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
        item { LifeTopBar(overline = "Mein Leben", title = "Verwaltung", subtitle = "Besitz, Unterlagen, Weiterbildung und System", large = true) }
        if (modules.isNotEmpty()) {
            item { SpineSection("Module", index = 0, shape = NodeShape.HEX, tone = NodeTone.ACTIVE, fullBleed = true) {} }
            modules.forEachIndexed { i, m ->
                item(key = m.title) { ModuleRow(m) }
            }
        }
        item { SpineSection("System", index = 1, shape = NodeShape.RING, fullBleed = true) {} }
        item { ModuleRow(LifeModule("System & Sicherheit", "Bewegung, Datenintegrität, Verschlüsselung", Glyph.SHIELD, onOpenSystem)) }
    }
}

@Composable
private fun ModuleRow(m: LifeModule) {
    LifeTimelineRow(nodeShape = NodeShape.HEX, nodeTone = NodeTone.NEUTRAL, onClick = m.open, onClickLabel = "${m.title} öffnen") {
        LifeGlyph(m.glyph, tint = ContinuumColors.Cyan)
        Column(Modifier.weight(1f)) {
            Text(m.title, style = Continuum.type.body)
            Text(m.subtitle, style = Continuum.type.small)
        }
        LifeGlyph(Glyph.CHEVRON_RIGHT, tint = ContinuumColors.Faint, size = 16.dp)
    }
}

/** System: Bewegung, Datenintegrität, Sicherheit. Jede Schaltfläche hier hat eine echte Funktion. */
@Composable
fun SystemScreen(container: AppContainer, systemAnimationsOff: Boolean, onBack: () -> Unit) {
    val motion by container.settings.motionEnabled.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<IntegrityReport?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        item { LifeTopBar(overline = "Mein Leben", title = "System & Sicherheit", onBack = onBack) }
        item {
            SpineSection("Bewegung", index = 0, shape = NodeShape.LINKED, tone = if (motion && !systemAnimationsOff) NodeTone.ACTIVE else NodeTone.NEUTRAL) {
                LifeToggle(
                    checked = motion,
                    onCheckedChange = { v -> scope.launch { container.settings.setMotionEnabled(v) } },
                    label = "Animationen",
                    description = "Lichtimpulse, Übergänge, Einstiegsanimation. Aus: alle Elemente erscheinen sofort.",
                )
                if (systemAnimationsOff) {
                    Text("Android-Einstellung „Animationen entfernen“ ist aktiv – Bewegung bleibt aus.", style = Continuum.type.small, color = ContinuumColors.Amber)
                }
                LifeLine(meaning = LineMeaning.TIME, pulse = true)
            }
        }
        item {
            SpineSection(
                "Datenintegrität", index = 1, shape = NodeShape.SQUARE,
                tone = when { report == null -> NodeTone.NEUTRAL; report!!.isHealthy -> NodeTone.DONE; else -> NodeTone.ALERT },
            ) {
                Text(
                    "Prüft, ob jede Buchung exakt mit ihren Aufteilungen übereinstimmt, ob alle Verweise gültig sind " +
                        "und ob verwaiste Datensätze existieren.",
                    style = Continuum.type.bodyMuted,
                )
                LifeButton(
                    text = if (checking) "Prüft …" else "Jetzt prüfen",
                    enabled = !checking,
                    style = LifeButtonStyle.SECONDARY,
                    glyph = Glyph.SHIELD,
                    onClick = {
                        checking = true
                        checkError = null
                        scope.launch {
                            try {
                                report = container.integrityChecker.run()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                checkError = "Prüfung fehlgeschlagen: ${e.javaClass.simpleName}"
                            } finally {
                                checking = false
                            }
                        }
                    },
                )
                report?.let { r ->
                    if (r.isHealthy) {
                        Text("${r.checkedTransactions} Buchungen geprüft – keine Abweichungen.", style = Continuum.type.body, color = ContinuumColors.Mint)
                    } else {
                        Text("${r.violations.size} Abweichungen gefunden:", style = Continuum.type.body, color = ContinuumColors.Alert)
                        r.violations.take(20).forEach { v ->
                            Text("${v.rule} · ${v.nodeId.take(8)} · ${v.detail}", style = Continuum.type.small)
                        }
                    }
                }
                checkError?.let { Text(it, style = Continuum.type.body, color = ContinuumColors.Alert) }
            }
        }
        item {
            SpineSection("Sicherheit", index = 2, shape = NodeShape.HEX) {
                InfoRow("Datenbank", "verschlüsselt · SQLCipher AES-256")
                InfoRow("Schlüssel", "Android Keystore · nicht exportierbar")
                InfoRow("Netzwerk", "keine Internet-Berechtigung")
                InfoRow("Android-Backup", "deaktiviert")
            }
        }
        item {
            SpineSection("Version", index = 3, shape = NodeShape.DOT) {
                InfoRow("LifeOS", version)
                InfoRow("Datenbankschema", "v${LifeOsDatabase.VERSION}")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label.uppercase(), style = Continuum.type.label, modifier = Modifier.weight(1f))
        Text(value, style = Continuum.type.small, color = ContinuumColors.Frost)
    }
}

/** Wird gezeigt, wenn der Datenbankschlüssel fehlt (ARCHITECTURE.md R-1). */
@Composable
fun RecoveryScreen(reason: String, onWipe: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        LifeTopBar(overline = "LifeOS", title = "Daten nicht lesbar")
        Column(Modifier.fillMaxWidth().padding48(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Der Schlüssel der verschlüsselten Datenbank ist nicht mehr verfügbar (z. B. nach einer " +
                    "Wiederherstellung auf einem anderen Gerät). Die vorhandenen Daten können ohne ihn nicht gelesen werden.",
                style = Continuum.type.body,
            )
            Text("Technischer Grund: $reason", style = Continuum.type.small)
            LifeLine(meaning = LineMeaning.OUTFLOW)
            when (step) {
                0 -> LifeButton("Neu beginnen …", onClick = { step = 1 }, style = LifeButtonStyle.SECONDARY)
                else -> {
                    Text("Alle lokalen LifeOS-Daten werden endgültig gelöscht. Das kann nicht rückgängig gemacht werden.", style = Continuum.type.body, color = ContinuumColors.Alert)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LifeButton("Endgültig löschen", onClick = onWipe, style = LifeButtonStyle.DANGER)
                        LifeButton("Abbrechen", onClick = { step = 0 }, style = LifeButtonStyle.GHOST)
                    }
                }
            }
        }
    }
}

private fun Modifier.padding48(): Modifier = this.padding(start = 48.dp, end = 20.dp)
