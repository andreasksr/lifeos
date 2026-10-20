package app.lifeos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifeos.AppContainer
import app.lifeos.core.designsystem.component.LifeButton
import app.lifeos.core.designsystem.component.LifeButtonStyle
import app.lifeos.core.designsystem.component.LifeChoiceChip
import app.lifeos.core.designsystem.component.LifeLine
import app.lifeos.core.designsystem.component.LifePanel
import app.lifeos.core.designsystem.component.LifeTopBar
import app.lifeos.core.designsystem.component.LineMeaning
import app.lifeos.core.designsystem.theme.Continuum
import app.lifeos.core.designsystem.theme.ContinuumColors
import app.lifeos.core.designsystem.theme.ContinuumSpacing
import app.lifeos.core.domain.ports.IntegrityReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** System: Bewegung, Datenintegrität, Sicherheit. Jede Schaltfläche hier hat eine echte Funktion. */
@Composable
fun SystemScreen(container: AppContainer, systemAnimationsOff: Boolean) {
    val motion by container.settings.motionEnabled.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<IntegrityReport?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(ContinuumColors.Obsidian),
    ) {
        LifeTopBar(overline = "LifeOS", title = "System")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ContinuumSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.l),
        ) {
            LifePanel(title = "Bewegung") {
                Text(
                    "Lichtpulse und Übergänge. Ausgeschaltet springen alle Elemente direkt in ihren Endzustand.",
                    style = Continuum.type.bodyMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.s)) {
                    LifeChoiceChip("An", selected = motion, onClick = { scope.launch { container.settings.setMotionEnabled(true) } })
                    LifeChoiceChip("Aus", selected = !motion, onClick = { scope.launch { container.settings.setMotionEnabled(false) } })
                }
                if (systemAnimationsOff) {
                    Text("Android-Einstellung „Animationen entfernen“ ist aktiv – Bewegung bleibt aus.", style = Continuum.type.bodyMuted, color = ContinuumColors.Violet)
                }
                LifeLine(meaning = LineMeaning.TIME, pulse = true)
            }

            LifePanel(title = "Datenintegrität") {
                Text(
                    "Prüft, ob jede Buchung exakt mit ihren Aufteilungen übereinstimmt, ob alle Verweise gültig sind " +
                        "und ob verwaiste Datensätze existieren.",
                    style = Continuum.type.bodyMuted,
                )
                LifeButton(
                    text = if (checking) "Prüft …" else "Jetzt prüfen",
                    enabled = !checking,
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
                        Text("✓ ${r.checkedTransactions} Buchungen geprüft – keine Abweichungen.", style = Continuum.type.body, color = ContinuumColors.Cyan)
                    } else {
                        Text("${r.violations.size} Abweichungen gefunden:", style = Continuum.type.body, color = ContinuumColors.Flare)
                        r.violations.take(20).forEach { v ->
                            Text("${v.rule} · ${v.nodeId.take(8)} · ${v.detail}", style = Continuum.type.bodyMuted)
                        }
                    }
                }
                checkError?.let { Text(it, style = Continuum.type.body, color = ContinuumColors.Flare) }
            }

            LifePanel(title = "Sicherheit") {
                InfoRow("Datenbank", "verschlüsselt · SQLCipher AES-256")
                InfoRow("Schlüssel", "Android Keystore · nicht exportierbar")
                InfoRow("Netzwerk", "keine Internet-Berechtigung")
                InfoRow("Android-Backup", "deaktiviert")
                Text(
                    "Wichtig: Ein verschlüsseltes, geräteunabhängiges Backup folgt in Phase 3. Bis dahin gehen die Daten " +
                        "bei Deinstallation oder Werksreset verloren.",
                    style = Continuum.type.bodyMuted,
                    color = ContinuumColors.Violet,
                )
            }

            LifePanel(title = "Version") {
                val context = LocalContext.current
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
                }
                InfoRow("LifeOS", version)
                InfoRow("Schema", "v${app.lifeos.core.database.LifeOsDatabase.VERSION}")
            }
            Spacer(Modifier.height(ContinuumSpacing.xxl))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = Continuum.type.label, modifier = Modifier.weight(1f))
        Text(value, style = Continuum.type.body)
    }
}

/** Wird gezeigt, wenn der Datenbankschlüssel fehlt (ARCHITECTURE.md R-1). */
@Composable
fun RecoveryScreen(reason: String, onWipe: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(ContinuumColors.Obsidian),
    ) {
        LifeTopBar(overline = "LifeOS", title = "Daten nicht lesbar")
        Column(
            Modifier.padding(horizontal = ContinuumSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ContinuumSpacing.l),
        ) {
            Text(
                "Der Schlüssel der verschlüsselten Datenbank ist nicht mehr verfügbar (z. B. nach einer " +
                    "Wiederherstellung auf einem anderen Gerät). Die vorhandenen Daten können ohne ihn nicht gelesen werden.",
                style = Continuum.type.body,
            )
            Text("Technischer Grund: $reason", style = Continuum.type.bodyMuted)
            LifeLine(meaning = LineMeaning.OUTFLOW)
            when (step) {
                0 -> LifeButton("Neu beginnen …", onClick = { step = 1 }, style = LifeButtonStyle.GHOST)
                else -> {
                    Text("Alle lokalen LifeOS-Daten werden endgültig gelöscht. Das kann nicht rückgängig gemacht werden.", style = Continuum.type.body, color = ContinuumColors.Flare)
                    Row(horizontalArrangement = Arrangement.spacedBy(ContinuumSpacing.s)) {
                        LifeButton("Endgültig löschen", onClick = onWipe, style = LifeButtonStyle.DANGER)
                        LifeButton("Abbrechen", onClick = { step = 0 }, style = LifeButtonStyle.GHOST)
                    }
                }
            }
        }
    }
}
